package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.dto.RoadmapGenerateRequest;
import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapStatus;
import com.cenergy.passed_backend.domain.roadmap.repository.MilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapMilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@SpringBootTest
class RoadmapGenerationConcurrencyIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(RoadmapGenerationConcurrencyIntegrationTest.class);

    @Autowired
    RoadmapGenerationClaimService claimService;
    @Autowired
    RoadmapPersistenceService persistenceService;
    @Autowired
    RoadmapRepository roadmapRepository;
    @Autowired
    RoadmapMilestoneRepository roadmapMilestoneRepository;
    @Autowired
    MilestoneRepository milestoneRepository;
    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> jobPostingIds = new ArrayList<>();
    private final List<Long> jobRoleIds = new ArrayList<>();
    private final List<Long> companyIds = new ArrayList<>();
    private final List<Long> industryIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        userIds.forEach(id -> jdbc.update("delete from users where id = ?", id));
        jobPostingIds.forEach(id -> jdbc.update("delete from job_postings where id = ?", id));
        jobRoleIds.forEach(id -> jdbc.update("delete from job_roles where id = ?", id));
        companyIds.forEach(id -> jdbc.update("delete from companies where id = ?", id));
        industryIds.forEach(id -> jdbc.update("delete from industries where id = ?", id));
    }

    @Test
    void concurrentEquivalentRequestsCreateOneRoadmapAndInvokeAiOnce() throws Exception {
        long userId = insertUser();
        long firstPosting = insertJobPosting();
        long secondPosting = insertJobPosting();
        AtomicInteger aiCalls = new AtomicInteger();
        CountDownLatch aiEntered = new CountDownLatch(1);
        CountDownLatch releaseAi = new CountDownLatch(1);
        RoadmapGenerationService generation = blockingGeneration(aiCalls, aiEntered, releaseAi);
        RoadmapCommandService command = command(userId, generation);
        CyclicBarrier start = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> first = executor.submit(() -> invoke(start, command,
                    List.of(secondPosting, firstPosting)));
            Future<Outcome> second = executor.submit(() -> invoke(start, command,
                    List.of(firstPosting, secondPosting, secondPosting)));

            assertThat(aiEntered.await(10, TimeUnit.SECONDS)).isTrue();
            releaseAi.countDown();
            List<Outcome> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.success()).singleElement()
                    .satisfies(outcome -> assertThat(outcome.errorCode())
                            .isIn(ErrorCode.ROADMAP_GENERATION_IN_PROGRESS, ErrorCode.ROADMAP_ALREADY_EXISTS));

            long successCount = outcomes.stream().filter(Outcome::success).count();
            long conflictCount = outcomes.size() - successCount;
            long savedRoadmapCount = countRoadmaps(userId, key(firstPosting, secondPosting));
            List<String> finalStatuses = statuses(userId, key(firstPosting, secondPosting));
            log.info("[CONCURRENCY PROOF] concurrentRequests={}, successes={}, conflicts={}, "
                            + "aiCalls={}, savedRoadmaps={}, finalStatuses={}",
                    outcomes.size(), successCount, conflictCount, aiCalls.get(),
                    savedRoadmapCount, finalStatuses);
        }

        assertThat(aiCalls).hasValue(1);
        assertThat(countRoadmaps(userId, key(firstPosting, secondPosting))).isEqualTo(1);
        assertThat(statuses(userId, key(firstPosting, secondPosting))).containsExactly("ACTIVE");
    }

    @Test
    void differentUsersDoNotBlockEachOther() throws Exception {
        long firstUser = insertUser();
        long secondUser = insertUser();
        long posting = insertJobPosting();
        CyclicBarrier aiBarrier = new CyclicBarrier(2);
        assertIndependentGeneration(command(firstUser, barrierGeneration(aiBarrier)),
                command(secondUser, barrierGeneration(aiBarrier)), List.of(posting), List.of(posting));
        assertThat(countRoadmaps(firstUser, key(posting))).isEqualTo(1);
        assertThat(countRoadmaps(secondUser, key(posting))).isEqualTo(1);
    }

    @Test
    void differentJobPostingSetsDoNotBlockEachOther() throws Exception {
        long userId = insertUser();
        long firstPosting = insertJobPosting();
        long secondPosting = insertJobPosting();
        CyclicBarrier aiBarrier = new CyclicBarrier(2);
        RoadmapGenerationService generation = barrierGeneration(aiBarrier);

        assertIndependentGeneration(command(userId, generation), command(userId, generation),
                List.of(firstPosting), List.of(secondPosting));

        assertThat(countRoadmaps(userId, key(firstPosting))).isEqualTo(1);
        assertThat(countRoadmaps(userId, key(secondPosting))).isEqualTo(1);
    }

    @Test
    void existingActiveOrCreatingRoadmapDoesNotCreateAnotherRecord() {
        long userId = insertUser();
        long activePosting = insertJobPosting();
        long creatingPosting = insertJobPosting();
        long activeId = claimService.acquire(userId, key(activePosting), List.of(activePosting)).roadmapId();
        persistenceService.complete(activeId, userId, result());
        long creatingId = claimService.acquire(userId, key(creatingPosting), List.of(creatingPosting)).roadmapId();
        RoadmapGenerationService generation = mock(RoadmapGenerationService.class);

        assertThatThrownBy(() -> command(userId, generation)
                .generate(new RoadmapGenerateRequest(List.of(activePosting))))
                .isInstanceOfSatisfying(RoadmapException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ROADMAP_ALREADY_EXISTS);
                    assertThat(exception.getRoadmapId()).isEqualTo(activeId);
                });
        assertThatThrownBy(() -> command(userId, generation)
                .generate(new RoadmapGenerateRequest(List.of(creatingPosting))))
                .isInstanceOfSatisfying(RoadmapException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ROADMAP_GENERATION_IN_PROGRESS);
                    assertThat(exception.getRoadmapId()).isEqualTo(creatingId);
                });

        assertThat(countRoadmaps(userId, key(activePosting))).isEqualTo(1);
        assertThat(countRoadmaps(userId, key(creatingPosting))).isEqualTo(1);
        verifyNoInteractions(generation);
    }

    @Test
    void failedRoadmapReleasesGenerationKeyForRetry() {
        long userId = insertUser();
        long posting = insertJobPosting();
        long failedId = claimService.acquire(userId, key(posting), List.of(posting)).roadmapId();
        claimService.markFailed(failedId, "test failure");
        RoadmapGenerationService generation = mock(RoadmapGenerationService.class);
        when(generation.generate(userId, List.of(posting))).thenReturn(result());

        command(userId, generation).generate(new RoadmapGenerateRequest(List.of(posting)));

        assertThat(countRoadmaps(userId, key(posting))).isEqualTo(2);
        assertThat(statuses(userId, key(posting))).containsExactlyInAnyOrder("FAILED", "ACTIVE");
        verify(generation).generate(userId, List.of(posting));
    }

    @Test
    void aiFailureTransitionsCreatingRoadmapToFailed() {
        long userId = insertUser();
        long posting = insertJobPosting();
        RoadmapGenerationService generation = mock(RoadmapGenerationService.class);
        when(generation.generate(anyLong(), anyList()))
                .thenThrow(new RoadmapException(ErrorCode.ROADMAP_GENERATION_FAILED, "AI failed"));

        assertThatThrownBy(() -> command(userId, generation)
                .generate(new RoadmapGenerateRequest(List.of(posting))))
                .isInstanceOf(RoadmapException.class);

        assertThat(statuses(userId, key(posting))).containsExactly("FAILED");
        assertThat(jdbc.queryForObject("""
                select failure_reason from roadmaps where user_id = ? and generation_key = ?
                """, String.class, userId, key(posting))).isEqualTo("Roadmap generation failed");
    }

    private void assertIndependentGeneration(RoadmapCommandService firstCommand,
                                             RoadmapCommandService secondCommand,
                                             List<Long> firstIds,
                                             List<Long> secondIds) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> first = executor.submit(() -> invoke(start, firstCommand, firstIds));
            Future<Outcome> second = executor.submit(() -> invoke(start, secondCommand, secondIds));
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .allMatch(Outcome::success);
        }
    }

    private RoadmapGenerationService blockingGeneration(AtomicInteger calls,
                                                        CountDownLatch entered,
                                                        CountDownLatch release) {
        RoadmapGenerationService generation = mock(RoadmapGenerationService.class);
        when(generation.generate(anyLong(), anyList())).thenAnswer(ignored -> {
            calls.incrementAndGet();
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("AI release latch timed out");
            }
            return result();
        });
        return generation;
    }

    private RoadmapGenerationService barrierGeneration(CyclicBarrier barrier) {
        RoadmapGenerationService generation = mock(RoadmapGenerationService.class);
        when(generation.generate(anyLong(), anyList())).thenAnswer(ignored -> {
            barrier.await(10, TimeUnit.SECONDS);
            return result();
        });
        return generation;
    }

    private Outcome invoke(CyclicBarrier start, RoadmapCommandService command, List<Long> ids) {
        try {
            start.await(10, TimeUnit.SECONDS);
            command.generate(new RoadmapGenerateRequest(ids));
            return new Outcome(true, null);
        } catch (RoadmapException exception) {
            return new Outcome(false, exception.getErrorCode());
        } catch (Exception unexpected) {
            throw new RuntimeException(unexpected);
        }
    }

    private RoadmapCommandService command(long userId, RoadmapGenerationService generation) {
        return new RoadmapCommandService(() -> userId, generation, claimService, persistenceService,
                roadmapRepository, roadmapMilestoneRepository, milestoneRepository);
    }

    private RoadmapGenerationResult result() {
        return new RoadmapGenerationResult("test roadmap", List.of());
    }

    private long countRoadmaps(long userId, String generationKey) {
        return jdbc.queryForObject("""
                select count(*) from roadmaps where user_id = ? and generation_key = ?
                """, Long.class, userId, generationKey);
    }

    private List<String> statuses(long userId, String generationKey) {
        return jdbc.queryForList("""
                select status from roadmaps where user_id = ? and generation_key = ? order by id
                """, String.class, userId, generationKey);
    }

    private String key(long... ids) {
        return java.util.Arrays.stream(ids).sorted()
                .mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private long insertUser() {
        String unique = Long.toUnsignedString(System.nanoTime());
        Long id = jdbc.queryForObject(
                "insert into users(name, email, password) values ('user', ?, 'pw') returning id",
                Long.class, unique + "@concurrency.test");
        userIds.add(id);
        return id;
    }

    private long insertJobPosting() {
        String unique = Long.toUnsignedString(System.nanoTime());
        long industryId = jdbc.queryForObject(
                "insert into industries(industry_name) values (?) returning id",
                Long.class, "concurrency-industry-" + unique);
        industryIds.add(industryId);
        long roleId = jdbc.queryForObject(
                "insert into job_roles(industry_id, job_role_name) values (?, 'role') returning id",
                Long.class, industryId);
        jobRoleIds.add(roleId);
        long companyId = jdbc.queryForObject(
                "insert into companies(company_name) values (?) returning id",
                Long.class, "concurrency-company-" + unique);
        companyIds.add(companyId);
        Long postingId = jdbc.queryForObject(
                "insert into job_postings(title, company_id, job_role_id) values ('posting', ?, ?) returning id",
                Long.class, companyId, roleId);
        jobPostingIds.add(postingId);
        return postingId;
    }

    private record Outcome(boolean success, ErrorCode errorCode) {
    }

}
