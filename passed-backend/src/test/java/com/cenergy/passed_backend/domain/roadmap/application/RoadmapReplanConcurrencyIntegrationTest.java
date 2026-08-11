package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.ai.client.RoadmapAiClient;
import com.cenergy.passed_backend.domain.roadmap.dto.RoadmapReplanApplyRequest;
import com.cenergy.passed_backend.domain.roadmap.entity.*;
import com.cenergy.passed_backend.domain.roadmap.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoadmapReplanConcurrencyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired RoadmapRepository roadmapRepository;
    @Autowired RoadmapSkillRepository skillRepository;
    @Autowired RoadmapMilestoneRepository linkRepository;
    @Autowired MilestoneRepository milestoneRepository;
    @Autowired LearningResourceRepository resourceRepository;
    @Autowired ResourceRecommendationRepository recommendationRepository;
    @Autowired RoadmapReplanRepository replanRepository;

    private Long userId;

    @AfterEach
    void cleanUp() {
        if (userId != null) jdbc.update("delete from users where id = ?", userId);
    }

    @Test
    void firstApplyAndSequentialReplayMutateMilestonesOnce() {
        Fixture fixture = fixture();

        apply(fixture.roadmapId(), fixture.token(), userId);
        long afterFirst = linkCount(fixture.roadmapId());
        apply(fixture.roadmapId(), fixture.token(), userId);

        assertThat(afterFirst).isEqualTo(1);
        assertThat(linkCount(fixture.roadmapId())).isEqualTo(afterFirst);
        assertThat(status(fixture.token())).isEqualTo("APPLIED");
        assertThat(appliedAt(fixture.token())).isNotNull();
    }

    @Test
    void concurrentApplyOfSameReadyTokenMutatesMilestonesOnce() throws Exception {
        Fixture fixture = fixture();
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> concurrentApply(start, fixture));
            Future<?> second = executor.submit(() -> concurrentApply(start, fixture));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertThat(linkCount(fixture.roadmapId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from milestones where user_id = ?", Long.class, userId))
                .isEqualTo(2); // original source plus exactly one compressed milestone
    }

    @Test
    void aNewTokenCanApplyAfterThePreviousToken() {
        Fixture first = fixture();
        apply(first.roadmapId(), first.token(), userId);
        UUID second = insertReplan(first.roadmapId(), currentSnapshot(first.roadmapId()),
                currentMilestoneId(first.roadmapId()), first.skillId());

        apply(first.roadmapId(), second, userId);

        assertThat(status(first.token())).isEqualTo("APPLIED");
        assertThat(status(second)).isEqualTo("APPLIED");
        assertThat(linkCount(first.roadmapId())).isEqualTo(1);
    }

    @Test
    void staleTokenIsRejectedAfterAnotherTokenChangesTheRoadmap() {
        Fixture fixture = fixture();
        UUID stale = insertReplan(fixture.roadmapId(), currentSnapshot(fixture.roadmapId()),
                fixture.milestoneId(), fixture.skillId());
        apply(fixture.roadmapId(), fixture.token(), userId);

        assertThatThrownBy(() -> apply(fixture.roadmapId(), stale, userId))
                .isInstanceOf(RoadmapException.class)
                .hasMessageContaining("changed after");
        assertThat(status(stale)).isEqualTo("READY");
    }

    @Test
    void completionAfterPreviewIsDetectedAsAConflict() {
        Fixture fixture = fixture();
        jdbc.update("update milestones set status = 'COMPLETED', progress_rate = 100 where id = ?",
                fixture.milestoneId());

        assertThatThrownBy(() -> apply(fixture.roadmapId(), fixture.token(), userId))
                .isInstanceOf(RoadmapException.class)
                .hasMessageContaining("changed after");
    }

    @Test
    void concurrentCompletionCannotBeLostByReplanApply() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch completionLocked = new CountDownLatch(1);
        CountDownLatch releaseCompletion = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> completion = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(ignored -> {
                        roadmapRepository.findOwnedForUpdate(fixture.roadmapId(), userId).orElseThrow();
                        Milestone milestone = milestoneRepository
                                .findOwnedForUpdate(fixture.milestoneId(), userId).orElseThrow();
                        completionLocked.countDown();
                        await(releaseCompletion);
                        milestone.changeCompletion(true, java.time.OffsetDateTime.now());
                    }));
            assertThat(completionLocked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> replan = executor.submit(() -> assertThatThrownBy(
                    () -> apply(fixture.roadmapId(), fixture.token(), userId))
                    .isInstanceOf(RoadmapException.class).hasMessageContaining("changed after"));
            releaseCompletion.countDown();
            completion.get(10, TimeUnit.SECONDS);
            replan.get(10, TimeUnit.SECONDS);
        }

        assertThat(jdbc.queryForObject("select status from milestones where id=?", String.class,
                fixture.milestoneId())).isEqualTo("COMPLETED");
        assertThat(linkCount(fixture.roadmapId())).isEqualTo(1);
        assertThat(status(fixture.token())).isEqualTo("READY");
    }

    @Test
    void failureAfterDeletionRollsBackLinksAndReadyStatus() {
        Fixture fixture = fixture();
        UUID broken = insertReplan(fixture.roadmapId(), currentSnapshot(fixture.roadmapId()),
                fixture.milestoneId(), Long.MAX_VALUE);

        assertThatThrownBy(() -> apply(fixture.roadmapId(), broken, userId))
                .isInstanceOf(RoadmapException.class)
                .hasMessageContaining("skill no longer exists");
        assertThat(linkCount(fixture.roadmapId())).isEqualTo(1);
        assertThat(status(broken)).isEqualTo("READY");
    }

    @Test
    void tokenCannotBeUsedByAnotherUserOrRoadmap() {
        Fixture fixture = fixture();
        long otherUser = insertUser();
        long otherRoadmap = insertRoadmap(otherUser);

        assertThatThrownBy(() -> apply(fixture.roadmapId(), fixture.token(), otherUser))
                .isInstanceOf(RoadmapException.class);
        assertThatThrownBy(() -> apply(otherRoadmap, fixture.token(), otherUser))
                .isInstanceOf(RoadmapException.class);
        jdbc.update("delete from users where id = ?", otherUser);
        assertThat(status(fixture.token())).isEqualTo("READY");
    }

    private void concurrentApply(CyclicBarrier start, Fixture fixture) {
        try {
            start.await(10, TimeUnit.SECONDS);
            apply(fixture.roadmapId(), fixture.token(), userId);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("latch timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }

    private void apply(long roadmapId, UUID token, long actingUser) {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
                service(actingUser).apply(roadmapId, new RoadmapReplanApplyRequest(token)));
    }

    private RoadmapReplanService service(long actingUser) {
        RoadmapEtaCalculator etaCalculator = new RoadmapEtaCalculator(60);
        RoadmapProgressSynchronizer progressSynchronizer = new RoadmapProgressSynchronizer(
                linkRepository, skillRepository, roadmapRepository, etaCalculator);
        return new RoadmapReplanService(() -> actingUser, roadmapRepository, skillRepository, linkRepository,
                milestoneRepository, resourceRepository, recommendationRepository, replanRepository,
                mock(RoadmapAiClient.class), etaCalculator, progressSynchronizer);
    }

    private Fixture fixture() {
        userId = insertUser();
        long roadmapId = insertRoadmap(userId);
        long skillId = jdbc.queryForObject("""
                insert into roadmap_skills(roadmap_id, standard_competency_id, standard_competency_name,
                  category, current_level, target_level, requirement_type, gap_level, frequency,
                  priority_score, priority, estimated_minutes, progress_rate)
                values (?, 101, 'Java', 'TECHNICAL_SKILL', 0, 2, 'REQUIRED', 2, 1, 1, 1, 120, 0)
                returning id
                """, Long.class, roadmapId);
        long milestoneId = jdbc.queryForObject("""
                insert into milestones(user_id, standard_competency_id, title, learning_objective,
                  completion_criteria, start_level, target_level, milestone_type, difficulty,
                  estimated_minutes, status, progress_rate)
                values (?, 101, 'source', 'learn', 'done', 0, 2, 'PRACTICE', 'BEGINNER', 120,
                  'NOT_STARTED', 0) returning id
                """, Long.class, userId);
        jdbc.update("insert into roadmap_milestones(roadmap_skill_id, milestone_id, learning_order, reuse_type, is_required) values (?, ?, 1, 'NEW', true)", skillId, milestoneId);
        UUID token = insertReplan(roadmapId, currentSnapshot(roadmapId), milestoneId, skillId);
        return new Fixture(roadmapId, skillId, milestoneId, token);
    }

    private UUID insertReplan(long roadmapId, List<RoadmapCompressionPlan.SourceSnapshot> snapshot,
                              long sourceMilestoneId, long skillId) {
        UUID token = UUID.randomUUID();
        var group = new RoadmapCompressionPlan.Group("g1", skillId, List.of(sourceMilestoneId), 60, 1,
                0, 2, "compressed", "description", "objective", "criteria",
                MilestoneType.PRACTICE, Difficulty.BEGINNER, "reason", List.of());
        try {
            String json = new ObjectMapper().writeValueAsString(new RoadmapCompressionPlan("summary", List.of(group), snapshot));
            jdbc.update("insert into roadmap_replans(token, roadmap_id, user_id, status, summary, decisions_json) values (?, ?, ?, 'READY', 'summary', cast(? as jsonb))",
                    token, roadmapId, userId, json);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
        return token;
    }

    private List<RoadmapCompressionPlan.SourceSnapshot> currentSnapshot(long roadmapId) {
        return jdbc.query("""
                select rm.id link_id, rm.roadmap_skill_id, rm.milestone_id, rm.learning_order,
                       rm.is_required, m.status
                from roadmap_milestones rm join roadmap_skills rs on rs.id = rm.roadmap_skill_id
                join milestones m on m.id = rm.milestone_id where rs.roadmap_id = ? order by rm.id
                """, (rs, row) -> new RoadmapCompressionPlan.SourceSnapshot(rs.getLong("link_id"),
                rs.getLong("roadmap_skill_id"), rs.getLong("milestone_id"), rs.getInt("learning_order"),
                rs.getBoolean("is_required"), MilestoneStatus.valueOf(rs.getString("status"))), roadmapId);
    }

    private long currentMilestoneId(long roadmapId) {
        return jdbc.queryForObject("select rm.milestone_id from roadmap_milestones rm join roadmap_skills rs on rs.id=rm.roadmap_skill_id where rs.roadmap_id=?", Long.class, roadmapId);
    }

    private long insertUser() {
        return jdbc.queryForObject("insert into users(name,email,password) values ('u',?,'pw') returning id",
                Long.class, UUID.randomUUID() + "@replan.test");
    }

    private long insertRoadmap(long owner) {
        return jdbc.queryForObject("insert into roadmaps(user_id,title,status,total_estimated_minutes,progress_rate,estimated_end_date,baseline_end_date) values (?,'roadmap','ACTIVE',120,0,current_date,current_date) returning id", Long.class, owner);
    }

    private long linkCount(long roadmapId) {
        return jdbc.queryForObject("select count(*) from roadmap_milestones rm join roadmap_skills rs on rs.id=rm.roadmap_skill_id where rs.roadmap_id=?", Long.class, roadmapId);
    }

    private String status(UUID token) {
        return jdbc.queryForObject("select status from roadmap_replans where token=?", String.class, token);
    }

    private Object appliedAt(UUID token) {
        return jdbc.queryForObject("select applied_at from roadmap_replans where token=?", Object.class, token);
    }

    private record Fixture(long roadmapId, long skillId, long milestoneId, UUID token) { }
}
