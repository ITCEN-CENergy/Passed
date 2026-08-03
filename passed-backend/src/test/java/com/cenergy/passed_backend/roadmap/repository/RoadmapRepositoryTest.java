package com.cenergy.passed_backend.roadmap.repository;

import com.cenergy.passed_backend.domain.roadmap.repository.*;
import com.cenergy.passed_backend.roadmap.entity.Milestone;
import com.cenergy.passed_backend.roadmap.entity.Roadmap;
import com.cenergy.passed_backend.roadmap.entity.RoadmapMilestone;
import com.cenergy.passed_backend.roadmap.entity.RoadmapSkill;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoadmapRepositoryTest {
    @Autowired
    RoadmapRepository roadmapRepository;
    @Autowired
    RoadmapJobPostingRepository roadmapJobPostingRepository;
    @Autowired
    RoadmapSkillRepository roadmapSkillRepository;
    @Autowired
    RoadmapSkillSourceRepository roadmapSkillSourceRepository;
    @Autowired
    MilestoneRepository milestoneRepository;
    @Autowired
    RoadmapMilestoneRepository roadmapMilestoneRepository;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void findsUserRoadmapsByCreatedAtAndIdDescending() {
        long userId = insertUser();
        long older = insertRoadmap(userId, "2026-01-01T00:00:00Z");
        long sameTimeLowerId = insertRoadmap(userId, "2026-01-02T00:00:00Z");
        long sameTimeHigherId = insertRoadmap(userId, "2026-01-02T00:00:00Z");
        insertRoadmap(insertUser(), "2026-02-01T00:00:00Z");

        assertThat(roadmapRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId))
                .extracting(Roadmap::getId)
                .containsExactly(sameTimeHigherId, sameTimeLowerId, older);
    }

    @Test
    void findsRoadmapOnlyWhenBothRoadmapAndUserMatch() {
        long ownerId = insertUser();
        long otherUserId = insertUser();
        long roadmapId = insertRoadmap(ownerId, "2026-01-01T00:00:00Z");

        assertThat(roadmapRepository.findByIdAndUserId(roadmapId, ownerId)).isPresent();
        assertThat(roadmapRepository.findByIdAndUserId(roadmapId, otherUserId)).isEmpty();
    }

    @Test
    void findsRoadmapJobPostings() {
        long roadmapId = insertRoadmap(insertUser(), "2026-01-01T00:00:00Z");
        long firstPostingId = insertJobPosting();
        long secondPostingId = insertJobPosting();
        insertRoadmapJobPosting(roadmapId, firstPostingId);
        insertRoadmapJobPosting(roadmapId, secondPostingId);

        assertThat(roadmapJobPostingRepository.findAllByRoadmapIdOrderByIdAsc(roadmapId))
                .extracting(value -> value.getJobPostingId())
                .containsExactly(firstPostingId, secondPostingId);
    }

    @Test
    void findsRoadmapSkillsByPriorityAscending() {
        long roadmapId = insertRoadmap(insertUser(), "2026-01-01T00:00:00Z");
        long priorityTwo = insertRoadmapSkill(roadmapId, 102, 2);
        long priorityOne = insertRoadmapSkill(roadmapId, 101, 1);

        assertThat(roadmapSkillRepository.findAllByRoadmapIdOrderByPriorityAscIdAsc(roadmapId))
                .extracting(RoadmapSkill::getId)
                .containsExactly(priorityOne, priorityTwo);
    }

    @Test
    void findsRoadmapMilestonesBySkillThenLearningOrder() {
        long userId = insertUser();
        long roadmapId = insertRoadmap(userId, "2026-01-01T00:00:00Z");
        long firstSkillId = insertRoadmapSkill(roadmapId, 101, 1);
        long secondSkillId = insertRoadmapSkill(roadmapId, 102, 2);
        long milestone1 = insertMilestone(userId, 101);
        long milestone2 = insertMilestone(userId, 101);
        long milestone3 = insertMilestone(userId, 102);
        long firstSkillOrderTwo = insertRoadmapMilestone(firstSkillId, milestone1, 2);
        long firstSkillOrderOne = insertRoadmapMilestone(firstSkillId, milestone2, 1);
        long secondSkillOrderOne = insertRoadmapMilestone(secondSkillId, milestone3, 1);

        List<RoadmapMilestone> result = roadmapMilestoneRepository
                .findAllByRoadmapSkillIds(List.of(secondSkillId, firstSkillId));

        assertThat(result).extracting(RoadmapMilestone::getId)
                .containsExactly(firstSkillOrderOne, firstSkillOrderTwo, secondSkillOrderOne);
    }

    @Test
    void findsMilestonesInOneBatchByIds() {
        long userId = insertUser();
        long first = insertMilestone(userId, 101);
        long second = insertMilestone(userId, 102);

        assertThat(milestoneRepository.findAllByIdInOrderByIdAsc(List.of(second, first)))
                .extracting(Milestone::getId)
                .containsExactly(first, second);
    }

    @Test
    void aggregatesJobPostingCountsByRoadmap() {
        long userId = insertUser();
        long first = insertRoadmap(userId, "2026-01-01T00:00:00Z");
        long second = insertRoadmap(userId, "2026-01-02T00:00:00Z");
        insertRoadmapJobPosting(first, insertJobPosting());
        insertRoadmapJobPosting(first, insertJobPosting());
        insertRoadmapJobPosting(second, insertJobPosting());

        assertCounts(roadmapJobPostingRepository.countByRoadmapIds(List.of(first, second)), first, 2, second, 1);
    }

    @Test
    void aggregatesSkillCountsByRoadmap() {
        long userId = insertUser();
        long first = insertRoadmap(userId, "2026-01-01T00:00:00Z");
        long second = insertRoadmap(userId, "2026-01-02T00:00:00Z");
        insertRoadmapSkill(first, 101, 1);
        insertRoadmapSkill(first, 102, 2);
        insertRoadmapSkill(second, 101, 1);

        assertCounts(roadmapSkillRepository.countByRoadmapIds(List.of(first, second)), first, 2, second, 1);
    }

    @Test
    void aggregatesMilestoneCountsByRoadmap() {
        long userId = insertUser();
        long firstRoadmap = insertRoadmap(userId, "2026-01-01T00:00:00Z");
        long secondRoadmap = insertRoadmap(userId, "2026-01-02T00:00:00Z");
        long firstSkill = insertRoadmapSkill(firstRoadmap, 101, 1);
        long secondSkill = insertRoadmapSkill(secondRoadmap, 102, 1);
        insertRoadmapMilestone(firstSkill, insertMilestone(userId, 101), 1);
        insertRoadmapMilestone(firstSkill, insertMilestone(userId, 101), 2);
        insertRoadmapMilestone(secondSkill, insertMilestone(userId, 102), 1);

        assertCounts(roadmapMilestoneRepository.countByRoadmapIds(List.of(firstRoadmap, secondRoadmap)),
                firstRoadmap, 2, secondRoadmap, 1);
    }

    @Test
    void rejectsDuplicateRoadmapJobPosting() {
        long roadmapId = insertRoadmap(insertUser(), "2026-01-01T00:00:00Z");
        long postingId = insertJobPosting();
        insertRoadmapJobPosting(roadmapId, postingId);

        assertThatThrownBy(() -> insertRoadmapJobPosting(roadmapId, postingId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateStandardCompetencyInRoadmap() {
        long roadmapId = insertRoadmap(insertUser(), "2026-01-01T00:00:00Z");
        insertRoadmapSkill(roadmapId, 101, 1);

        assertThatThrownBy(() -> insertRoadmapSkill(roadmapId, 101, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateRoadmapSkillSource() {
        long roadmapId = insertRoadmap(insertUser(), "2026-01-01T00:00:00Z");
        long skillId = insertRoadmapSkill(roadmapId, 101, 1);
        long postingId = insertJobPosting();
        insertRoadmapSkillSource(skillId, postingId, 101);

        assertThatThrownBy(() -> insertRoadmapSkillSource(skillId, postingId, 101))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateMilestoneInRoadmapSkill() {
        long userId = insertUser();
        long roadmapId = insertRoadmap(userId, "2026-01-01T00:00:00Z");
        long skillId = insertRoadmapSkill(roadmapId, 101, 1);
        long milestoneId = insertMilestone(userId, 101);
        insertRoadmapMilestone(skillId, milestoneId, 1);

        assertThatThrownBy(() -> insertRoadmapMilestone(skillId, milestoneId, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateLearningOrderInRoadmapSkill() {
        long userId = insertUser();
        long roadmapId = insertRoadmap(userId, "2026-01-01T00:00:00Z");
        long skillId = insertRoadmapSkill(roadmapId, 101, 1);
        insertRoadmapMilestone(skillId, insertMilestone(userId, 101), 1);

        assertThatThrownBy(() -> insertRoadmapMilestone(skillId, insertMilestone(userId, 101), 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertCounts(Collection<RoadmapCount> counts,
                              long firstRoadmapId, long firstCount,
                              long secondRoadmapId, long secondCount) {
        Map<Long, Long> result = counts.stream()
                .collect(Collectors.toMap(RoadmapCount::getRoadmapId, RoadmapCount::getCount));
        assertThat(result).containsEntry(firstRoadmapId, firstCount).containsEntry(secondRoadmapId, secondCount);
    }

    private long insertUser() {
        String unique = Long.toUnsignedString(System.nanoTime());
        return id("insert into users(name, email, password) values ('user', ?, 'pw') returning id",
                unique + "@test.local");
    }

    private long insertRoadmap(long userId, String createdAt) {
        return id("""
                        insert into roadmaps(user_id, status, total_estimated_minutes, progress_rate, created_at, updated_at)
                        values (?, 'CREATING', 0, 0, ?, ?) returning id
                        """, userId, Timestamp.from(OffsetDateTime.parse(createdAt).toInstant()),
                Timestamp.from(OffsetDateTime.parse(createdAt).toInstant()));
    }

    private long insertJobPosting() {
        String unique = Long.toUnsignedString(System.nanoTime());
        long industryId = id("insert into industries(industry_name) values (?) returning id", "industry-" + unique);
        long roleId = id("insert into job_roles(industry_id, job_role_name) values (?, 'role') returning id", industryId);
        long companyId = id("insert into companies(company_name) values (?) returning id", "company-" + unique);
        return id("insert into job_postings(title, company_id, job_role_id) values ('posting', ?, ?) returning id",
                companyId, roleId);
    }

    private long insertRoadmapJobPosting(long roadmapId, long postingId) {
        return id("insert into roadmap_job_postings(roadmap_id, job_posting_id) values (?, ?) returning id",
                roadmapId, postingId);
    }

    private long insertRoadmapSkill(long roadmapId, long competencyId, int priority) {
        return id("""
                insert into roadmap_skills(roadmap_id, standard_competency_id, standard_competency_name,
                  category, current_level, target_level, requirement_type, gap_level, frequency,
                  priority_score, priority, estimated_minutes, progress_rate)
                values (?, ?, 'Java', 'TECHNICAL_SKILL', 0, 1, 'REQUIRED', 1, 1, 1, ?, 0, 0) returning id
                """, roadmapId, competencyId, priority);
    }

    private long insertMilestone(long userId, long competencyId) {
        return id("""
                insert into milestones(user_id, standard_competency_id, title, learning_objective,
                  completion_criteria, start_level, target_level, milestone_type, difficulty,
                  estimated_minutes, status, progress_rate)
                values (?, ?, 'title', 'objective', 'criteria', 0, 1, 'CONCEPT', 'BEGINNER',
                  30, 'NOT_STARTED', 0) returning id
                """, userId, competencyId);
    }

    private long insertRoadmapMilestone(long skillId, long milestoneId, int learningOrder) {
        return id("""
                insert into roadmap_milestones(roadmap_skill_id, milestone_id, learning_order,
                  reuse_type, is_required) values (?, ?, ?, 'NEW', true) returning id
                """, skillId, milestoneId, learningOrder);
    }

    private long insertRoadmapSkillSource(long skillId, long postingId, long competencyId) {
        return id("""
                insert into roadmap_skill_sources(roadmap_skill_id, job_posting_id,
                  standard_competency_id, standard_competency_name, category, current_level,
                  requirement_type, target_level, gap_level)
                values (?, ?, ?, 'Java', 'TECHNICAL_SKILL', 0, 'REQUIRED', 1, 1) returning id
                """, skillId, postingId, competencyId);
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
