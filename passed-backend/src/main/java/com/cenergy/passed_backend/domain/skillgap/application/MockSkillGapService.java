package com.cenergy.passed_backend.domain.skillgap.application;

import com.cenergy.passed_backend.domain.skillgap.dto.CompetencyGapResponse;
import com.cenergy.passed_backend.domain.skillgap.dto.SkillGapResponse;
import com.cenergy.passed_backend.domain.skillgap.model.ValidatedSkillGapResult;
import com.cenergy.passed_backend.domain.skillgap.validation.SkillGapResponseValidator;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import com.cenergy.passed_backend.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.roadmap.entity.RequirementType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnMissingBean(value = SkillGapService.class, ignored = MockSkillGapService.class)
public class MockSkillGapService implements SkillGapService {
    private static final Map<Long, List<CompetencyGapResponse>> GAPS_BY_JOB_POSTING = Map.of(
            101L, List.of(
                    gap(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL, RequirementType.REQUIRED, 1, 3, "Docker 기본 명령어 학습 및 실습 경험"),
                    gap(2L, "AWS", CompetencyCategory.TECHNICAL_SKILL, RequirementType.PREFERRED, 0, 2, null)),
            102L, List.of(
                    gap(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL, RequirementType.REQUIRED, 2, 3, "컨테이너 배포 경험"),
                    gap(3L, "SQLD", CompetencyCategory.CERTIFICATION, RequirementType.PREFERRED, 0, 1, null)),
            103L, List.of(
                    gap(2L, "AWS", CompetencyCategory.TECHNICAL_SKILL, RequirementType.REQUIRED, 1, 3, "EC2 실습 경험"),
                    gap(4L, "협업", CompetencyCategory.BEHAVIORAL_TRAIT, RequirementType.RELATED, 2, 3, null)),
            104L, List.of()
    );

    private final SkillGapResponseValidator validator;

    public MockSkillGapService(SkillGapResponseValidator validator) {
        this.validator = validator;
    }

    @Override
    public ValidatedSkillGapResult getCompetencyGaps(Long jobPostingId, Long userId) {
        List<CompetencyGapResponse> gaps = GAPS_BY_JOB_POSTING.get(jobPostingId);
        if (gaps == null) {
            throw new SkillGapException(ErrorCode.SKILL_GAP_NOT_FOUND,
                    "Skill gap mock data not found for jobPostingId=" + jobPostingId);
        }
        return validator.validate(userId, jobPostingId, new SkillGapResponse(userId, jobPostingId, gaps));
    }

    private static CompetencyGapResponse gap(
            Long id, String name, CompetencyCategory category, RequirementType requirementType,
            int currentLevel, int targetLevel, String evidence
    ) {
        return new CompetencyGapResponse(id, name, category, requirementType,
                currentLevel, targetLevel, Math.max(targetLevel - currentLevel, 0), evidence);
    }
}
