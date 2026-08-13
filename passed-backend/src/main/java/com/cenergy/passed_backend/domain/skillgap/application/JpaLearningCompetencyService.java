package com.cenergy.passed_backend.domain.skillgap.application;

import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendation;
import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendationSkillDetail;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationSkillDetailRepository;
import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.skill.repository.UserSkillEvidenceRepository;
import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyItem;
import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Primary
@Transactional(readOnly = true)
public class JpaLearningCompetencyService implements LearningCompetencyService {

    private static final Logger log = LoggerFactory.getLogger(JpaLearningCompetencyService.class);

    private final JobRecommendationRepository jobRecommendationRepository;
    private final JobRecommendationSkillDetailRepository skillDetailRepository;
    private final UserSkillEvidenceRepository userSkillEvidenceRepository;

    public JpaLearningCompetencyService(
            JobRecommendationRepository jobRecommendationRepository,
            JobRecommendationSkillDetailRepository skillDetailRepository,
            UserSkillEvidenceRepository userSkillEvidenceRepository
    ) {
        this.jobRecommendationRepository = jobRecommendationRepository;
        this.skillDetailRepository = skillDetailRepository;
        this.userSkillEvidenceRepository = userSkillEvidenceRepository;
    }

    @Override
    public LearningCompetencyResponse getLearningCompetencies(Long jobPostingId, Long userId) {
        JobRecommendation recommendation = jobRecommendationRepository
                .findFirstByJobPostingIdAndRecommendationRunUserIdOrderByRecommendationRunStartedAtDescIdDesc(
                        jobPostingId,
                        userId
                )
                .orElseThrow(() -> new SkillGapException(
                        ErrorCode.SKILL_GAP_NOT_FOUND,
                        "Job recommendation not found for jobPostingId=" + jobPostingId
                                + ", userId=" + userId
                ));

        List<JobRecommendationSkillDetail> details =
                skillDetailRepository.findAllByJobRecommendationIdOrderByIdAsc(recommendation.getId());
        Map<Long, String> evidenceBySkillId = findLatestEvidenceBySkillId(details, userId);

        List<LearningCompetencyItem> competencies = details.stream()
                .map(detail -> toItem(detail, evidenceBySkillId.get(detail.getSkill().getId())))
                .toList();

        log.info("Learning competencies loaded: userId={}, jobPostingId={}, recommendationId={}, count={}",
                userId, jobPostingId, recommendation.getId(), competencies.size());
        competencies.forEach(item -> log.info(
                "Learning competency: jobPostingId={}, competencyId={}, name='{}', category={}, "
                        + "requirementType={}, currentLevel={}, targetLevel={}, hasEvidence={}",
                jobPostingId,
                item.standardCompetencyId(),
                item.standardCompetencyName(),
                item.category(),
                item.requirementType(),
                item.currentLevel(),
                item.targetLevel(),
                item.currentLevelEvidence() != null && !item.currentLevelEvidence().isBlank()
        ));

        return new LearningCompetencyResponse(userId, jobPostingId, competencies);
    }

    private Map<Long, String> findLatestEvidenceBySkillId(
            List<JobRecommendationSkillDetail> details,
            Long userId
    ) {
        List<Long> skillIds = details.stream()
                .map(detail -> detail.getSkill().getId())
                .distinct()
                .toList();
        if (skillIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> evidenceBySkillId = new LinkedHashMap<>();
        userSkillEvidenceRepository.findAllByUserIdAndSkillIds(userId, skillIds)
                .forEach(evidence -> evidenceBySkillId.putIfAbsent(
                        evidence.getUserSkill().getSkill().getId(),
                        evidence.getEvidenceText()
                ));
        return evidenceBySkillId;
    }

    private LearningCompetencyItem toItem(
            JobRecommendationSkillDetail detail,
            String currentLevelEvidence
    ) {
        Skill skill = detail.getSkill();
        return new LearningCompetencyItem(
                skill.getId(),
                skill.getName(),
                CompetencyCategory.valueOf(skill.getCategory().name()),
                RequirementType.valueOf(detail.getSkillType().name()),
                detail.getUserLevel() == null ? null : detail.getUserLevel().intValue(),
                (int) detail.getRequiredLevel(),
                currentLevelEvidence
        );
    }
}
