package com.cenergy.passed_backend.domain.skillgap.application;

import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyItem;
import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.error.SkillGapException;
import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnMissingBean(value = LearningCompetencyService.class, ignored = MockLearningCompetencyService.class)
public class MockLearningCompetencyService implements LearningCompetencyService {
    private static final Map<Long, List<LearningCompetencyItem>> COMPETENCIES_BY_JOB_POSTING = Map.of(
            101L, List.of(
                    gap(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL, RequirementType.REQUIRED, 1, 3, "Docker 기본 명령어 학습 및 실습 경험"),
                    gap(2L, "AWS", CompetencyCategory.TECHNICAL_SKILL, RequirementType.PREFERRED, 1, 2, null)),
            102L, List.of(
                    gap(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL, RequirementType.REQUIRED, 2, 3, "컨테이너 배포 경험"),
                    gap(3L, "SQLD", CompetencyCategory.CERTIFICATION, RequirementType.PREFERRED, 0, 1, null)),
            103L, List.of(
                    gap(2L, "AWS", CompetencyCategory.TECHNICAL_SKILL, RequirementType.REQUIRED, 1, 3, "EC2 실습 경험"),
                    gap(4L, "협업", CompetencyCategory.BEHAVIORAL_TRAIT, RequirementType.RELATED, 2, 3, null)),
            104L, List.of(),
            105L, List.of(
                    gap(1L, "Docker", CompetencyCategory.TECHNICAL_SKILL, RequirementType.PREFERRED, 1, 3, "Docker Compose 학습 필요"),
                    gap(2L, "AWS", CompetencyCategory.TECHNICAL_SKILL, RequirementType.RELATED, 2, 3, "S3 및 IAM 기초 경험"),
                    gap(5L, "Java", CompetencyCategory.TECHNICAL_SKILL, RequirementType.REQUIRED, 2, 3, "Spring Boot 프로젝트 경험")),
            106L, List.of(
                    gap(5L, "Java", CompetencyCategory.TECHNICAL_SKILL, RequirementType.PREFERRED, 3, 3, "Java 실무 경험"),
                    gap(6L, "Kubernetes", CompetencyCategory.TECHNICAL_SKILL, RequirementType.RELATED, 1, 3, null),
                    gap(7L, "문제 해결", CompetencyCategory.BEHAVIORAL_TRAIT, RequirementType.PREFERRED, 2, 3, "장애 분석 경험")),
            107L, List.of(
                    gap(6L, "Kubernetes", CompetencyCategory.TECHNICAL_SKILL, RequirementType.REQUIRED, 1, 3, "배포 자동화 학습 필요"),
                    gap(7L, "문제 해결", CompetencyCategory.BEHAVIORAL_TRAIT, RequirementType.RELATED, 2, 3, "로그 기반 분석 경험"),
                    gap(8L, "AWS SAA", CompetencyCategory.CERTIFICATION, RequirementType.PREFERRED, 0, 1, null)),
            108L, List.of(
                    gap(9L, "REST API 설계", CompetencyCategory.EXPERIENCE, RequirementType.REQUIRED, 3, 3, "API 설계 및 운영 경험"),
                    gap(10L, "CI/CD", CompetencyCategory.EXPERIENCE, RequirementType.PREFERRED, 1, 3, "GitHub Actions 기초 경험"))
    );

    @Override
    public LearningCompetencyResponse getLearningCompetencies(Long jobPostingId, Long userId) {
        List<LearningCompetencyItem> competencies = COMPETENCIES_BY_JOB_POSTING.get(jobPostingId);
        if (competencies == null) {
            throw new SkillGapException(ErrorCode.SKILL_GAP_NOT_FOUND,
                    "Skill gap mock data not found for jobPostingId=" + jobPostingId);
        }
        return new LearningCompetencyResponse(userId, jobPostingId, competencies);
    }

    private static LearningCompetencyItem gap(
            Long id, String name, CompetencyCategory category, RequirementType requirementType,
            int currentLevel, int targetLevel, String currentLevelEvidence
    ) {
        return new LearningCompetencyItem(id, name, category, requirementType,
                currentLevel, targetLevel, currentLevelEvidence);
    }
}
