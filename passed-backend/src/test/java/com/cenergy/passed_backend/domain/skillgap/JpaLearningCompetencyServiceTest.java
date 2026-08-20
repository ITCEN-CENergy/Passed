package com.cenergy.passed_backend.domain.skillgap;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendation;
import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendationSkillDetail;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationSkillDetailRepository;
import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;
import com.cenergy.passed_backend.domain.skill.entity.UserSkill;
import com.cenergy.passed_backend.domain.skill.entity.UserSkillEvidence;
import com.cenergy.passed_backend.domain.skill.repository.UserSkillEvidenceRepository;
import com.cenergy.passed_backend.domain.skillgap.application.JpaLearningCompetencyService;
import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaLearningCompetencyServiceTest {

    private JobRecommendationRepository recommendationRepository;
    private JobRecommendationSkillDetailRepository skillDetailRepository;
    private UserSkillEvidenceRepository evidenceRepository;
    private JpaLearningCompetencyService service;

    @BeforeEach
    void setUp() {
        recommendationRepository = mock(JobRecommendationRepository.class);
        skillDetailRepository = mock(JobRecommendationSkillDetailRepository.class);
        evidenceRepository = mock(UserSkillEvidenceRepository.class);
        service = new JpaLearningCompetencyService(
                recommendationRepository,
                skillDetailRepository,
                evidenceRepository
        );
    }

    @Test
    void mapsRecommendationDetailAndLatestEvidenceToResponse() {
        JobRecommendation recommendation = mock(JobRecommendation.class);
        JobRecommendationSkillDetail detail = mock(JobRecommendationSkillDetail.class);
        Skill skill = mock(Skill.class);
        UserSkillEvidence latestEvidence = evidence(skill, "latest evidence");
        UserSkillEvidence olderEvidence = evidence(skill, "older evidence");

        when(recommendation.getId()).thenReturn(50L);
        when(skill.getId()).thenReturn(7L);
        when(skill.getName()).thenReturn("Java");
        when(skill.getCategory()).thenReturn(SkillCategory.TECHNICAL_SKILL);
        when(detail.getSkill()).thenReturn(skill);
        when(detail.getSkillType()).thenReturn(JobPostingSkillType.REQUIRED);
        when(detail.getRequiredLevel()).thenReturn((short) 3);
        when(detail.getUserLevel()).thenReturn((short) 2);
        when(recommendationRepository
                .findFirstByJobPostingIdAndRecommendationRunUserIdOrderByRecommendationRunStartedAtDescIdDesc(
                        100L,
                        10L
                )).thenReturn(Optional.of(recommendation));
        when(skillDetailRepository.findAllByJobRecommendationIdOrderByIdAsc(50L))
                .thenReturn(List.of(detail));
        when(evidenceRepository.findAllByUserIdAndSkillIds(10L, List.of(7L)))
                .thenReturn(List.of(latestEvidence, olderEvidence));

        LearningCompetencyResponse response = service.getLearningCompetencies(100L, 10L);

        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.jobPostingId()).isEqualTo(100L);
        assertThat(response.competencies()).singleElement().satisfies(item -> {
            assertThat(item.standardCompetencyId()).isEqualTo(7L);
            assertThat(item.standardCompetencyName()).isEqualTo("Java");
            assertThat(item.category()).isEqualTo(CompetencyCategory.TECHNICAL_SKILL);
            assertThat(item.requirementType()).isEqualTo(RequirementType.REQUIRED);
            assertThat(item.currentLevel()).isEqualTo(2);
            assertThat(item.targetLevel()).isEqualTo(3);
            assertThat(item.currentLevelEvidence()).isEqualTo("latest evidence");
        });
    }

    @Test
    void doesNotQueryEvidenceWhenRecommendationHasNoSkillDetails() {
        JobRecommendation recommendation = mock(JobRecommendation.class);
        when(recommendation.getId()).thenReturn(50L);
        when(recommendationRepository
                .findFirstByJobPostingIdAndRecommendationRunUserIdOrderByRecommendationRunStartedAtDescIdDesc(
                        100L,
                        10L
                )).thenReturn(Optional.of(recommendation));
        when(skillDetailRepository.findAllByJobRecommendationIdOrderByIdAsc(50L))
                .thenReturn(List.of());

        LearningCompetencyResponse response = service.getLearningCompetencies(100L, 10L);

        assertThat(response.competencies()).isEmpty();
        verify(evidenceRepository, never()).findAllByUserIdAndSkillIds(10L, List.of());
    }

    @Test
    void excludesBehavioralTraitsFromLearningCompetencies() {
        JobRecommendation recommendation = mock(JobRecommendation.class);
        JobRecommendationSkillDetail behavioralDetail = mock(JobRecommendationSkillDetail.class);
        Skill behavioralSkill = mock(Skill.class);
        when(recommendation.getId()).thenReturn(50L);
        when(behavioralSkill.getCategory()).thenReturn(SkillCategory.BEHAVIORAL_TRAIT);
        when(behavioralDetail.getSkill()).thenReturn(behavioralSkill);
        when(recommendationRepository
                .findFirstByJobPostingIdAndRecommendationRunUserIdOrderByRecommendationRunStartedAtDescIdDesc(
                        100L, 10L)).thenReturn(Optional.of(recommendation));
        when(skillDetailRepository.findAllByJobRecommendationIdOrderByIdAsc(50L))
                .thenReturn(List.of(behavioralDetail));

        LearningCompetencyResponse response = service.getLearningCompetencies(100L, 10L);

        assertThat(response.competencies()).isEmpty();
        verify(evidenceRepository, never()).findAllByUserIdAndSkillIds(10L, List.of());
    }

    @Test
    void throwsNotFoundWhenUserHasNoRecommendationForPosting() {
        when(recommendationRepository
                .findFirstByJobPostingIdAndRecommendationRunUserIdOrderByRecommendationRunStartedAtDescIdDesc(
                        100L,
                        10L
                )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLearningCompetencies(100L, 10L))
                .isInstanceOf(SkillGapException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SKILL_GAP_NOT_FOUND);
    }

    private UserSkillEvidence evidence(Skill skill, String text) {
        UserSkill userSkill = mock(UserSkill.class);
        UserSkillEvidence evidence = mock(UserSkillEvidence.class);
        when(userSkill.getSkill()).thenReturn(skill);
        when(evidence.getUserSkill()).thenReturn(userSkill);
        when(evidence.getEvidenceText()).thenReturn(text);
        return evidence;
    }
}
