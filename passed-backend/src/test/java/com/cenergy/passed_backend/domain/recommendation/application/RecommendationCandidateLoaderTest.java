package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkill;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingSkillRepository;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationCandidateLoaderTest {
    private JobPostingRepository jobPostingRepository;
    private JobPostingSkillRepository jobPostingSkillRepository;
    private RecommendationCandidateLoader loader;

    @BeforeEach
    void setUp() {
        jobPostingRepository = mock(JobPostingRepository.class);
        jobPostingSkillRepository = mock(JobPostingSkillRepository.class);
        loader = new RecommendationCandidateLoader(jobPostingRepository, jobPostingSkillRepository);
    }

    @Test
    void loadsCandidatesAndClassifiesPostingSkillsInBulk() {
        List<JobPostingSkill> postingSkills = List.of(
                postingSkill(100L, 10L, 1, JobPostingSkillType.RELATED),
                postingSkill(100L, 10L, 2, JobPostingSkillType.PREFERRED),
                postingSkill(100L, 10L, 3, JobPostingSkillType.REQUIRED),
                postingSkill(100L, 20L, 2, JobPostingSkillType.PREFERRED),
                postingSkill(100L, 30L, 1, JobPostingSkillType.RELATED)
        );
        when(jobPostingRepository.findCandidateIdsByJobRoleIds(List.of(227L, 239L)))
                .thenReturn(List.of(100L, 200L));
        when(jobPostingSkillRepository
                .findAllByJobPosting_IdInOrderByJobPosting_IdAscSkill_IdAsc(List.of(100L, 200L)))
                .thenReturn(postingSkills);

        Map<Long, PostingSkillBundle> result = loader.loadByJobRoleIds(
                List.of(239L, 227L, 239L)
        );

        assertEquals(List.of(100L, 200L), result.keySet().stream().toList());
        PostingSkillBundle first = result.get(100L);
        assertEquals(1, first.requiredSkillCount());
        assertEquals(1, first.preferredSkillCount());
        assertEquals(1, first.relatedSkillCount());
        assertEquals(10L, first.requiredSkills().getFirst().skillId());
        assertEquals((short) 3, first.requiredSkills().getFirst().requiredLevel());
        assertEquals(20L, first.preferredSkills().getFirst().skillId());
        assertEquals(30L, first.relatedSkills().getFirst().skillId());

        PostingSkillBundle second = result.get(200L);
        assertTrue(second.requiredSkills().isEmpty());
        assertTrue(second.preferredSkills().isEmpty());
        assertTrue(second.relatedSkills().isEmpty());
        verify(jobPostingSkillRepository, times(1))
                .findAllByJobPosting_IdInOrderByJobPosting_IdAscSkill_IdAsc(any());
    }

    @Test
    void splitsPostingSkillQueriesIntoFixedSizeBatches() {
        List<Long> candidateIds = LongStream.rangeClosed(1, 501).boxed().toList();
        when(jobPostingRepository.findCandidateIdsByJobRoleIds(List.of(227L)))
                .thenReturn(candidateIds);
        when(jobPostingSkillRepository
                .findAllByJobPosting_IdInOrderByJobPosting_IdAscSkill_IdAsc(any()))
                .thenReturn(List.of());

        Map<Long, PostingSkillBundle> result = loader.loadByJobRoleIds(List.of(227L));

        assertEquals(501, result.size());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(jobPostingSkillRepository, times(2))
                .findAllByJobPosting_IdInOrderByJobPosting_IdAscSkill_IdAsc(captor.capture());
        assertEquals(500, captor.getAllValues().get(0).size());
        assertEquals(1, captor.getAllValues().get(1).size());
    }

    @Test
    void skipsQueriesWhenNoJobRoleIsSelected() {
        assertTrue(loader.loadByJobRoleIds(List.of()).isEmpty());

        verify(jobPostingRepository, never()).findCandidateIdsByJobRoleIds(any());
        verify(jobPostingSkillRepository, never())
                .findAllByJobPosting_IdInOrderByJobPosting_IdAscSkill_IdAsc(any());
    }

    private JobPostingSkill postingSkill(
            Long jobPostingId,
            Long skillId,
            int skillLevel,
            JobPostingSkillType skillType
    ) {
        JobPosting jobPosting = mock(JobPosting.class);
        when(jobPosting.getId()).thenReturn(jobPostingId);

        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(skillId);

        JobPostingSkill postingSkill = mock(JobPostingSkill.class);
        when(postingSkill.getJobPosting()).thenReturn(jobPosting);
        when(postingSkill.getSkill()).thenReturn(skill);
        when(postingSkill.getSkillLevel()).thenReturn((short) skillLevel);
        when(postingSkill.getSkillType()).thenReturn(skillType);
        return postingSkill;
    }
}
