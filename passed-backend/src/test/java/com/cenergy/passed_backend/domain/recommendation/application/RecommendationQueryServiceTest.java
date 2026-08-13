package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.jobposting.entity.Company;
import com.cenergy.passed_backend.domain.jobposting.entity.CompanySize;
import com.cenergy.passed_backend.domain.jobposting.entity.Industry;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPostingSkillType;
import com.cenergy.passed_backend.domain.jobposting.entity.JobRole;
import com.cenergy.passed_backend.domain.recommendation.dto.RecommendationDetailResponse;
import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendation;
import com.cenergy.passed_backend.domain.recommendation.entity.JobRecommendationSkillDetail;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationGrade;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRun;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationRunStatus;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationSkillDetailRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.RecommendationRunRepository;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.skill.repository.SkillRepository;
import com.cenergy.passed_backend.domain.roadmap.application.CurrentUserIdProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationQueryServiceTest {

    @Test
    void restoresLatestPreferenceResultAndLatestJobPostingReportForCurrentUser() {
        RecommendationRunRepository runRepository = mock(RecommendationRunRepository.class);
        JobRecommendationRepository recommendationRepository = mock(JobRecommendationRepository.class);
        JobRecommendationSkillDetailRepository detailRepository =
                mock(JobRecommendationSkillDetailRepository.class);
        SkillRepository skillRepository = mock(SkillRepository.class);
        CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(2L);
        RecommendationRun run = mock(RecommendationRun.class);
        when(run.getId()).thenReturn(10L);
        when(run.getStatus()).thenReturn(RecommendationRunStatus.COMPLETED);
        when(run.getUserSkillSnapshot()).thenReturn(Map.of("skills", List.of()));
        when(run.getPreferenceSnapshot()).thenReturn(Map.of(
                "industryId", 8L,
                "industryName", "AI·개발·데이터",
                "jobRoles", List.of(Map.of("jobRoleId", 227L, "jobRoleName", "AI/ML엔지니어"))
        ));
        JobRecommendation recommendation = recommendation(posting());
        when(recommendation.getRecommendationRun()).thenReturn(run);
        when(runRepository.findLatestCompletedPreferenceRun(2L)).thenReturn(Optional.of(run));
        when(recommendationRepository.findAllByRecommendationRunIdOrderByRankOrderAsc(10L))
                .thenReturn(List.of(recommendation));
        when(recommendationRepository
                .findFirstByJobPostingIdAndRecommendationRunUserIdOrderByRecommendationRunStartedAtDescIdDesc(
                        200L,
                        2L
                )).thenReturn(Optional.of(recommendation));
        when(detailRepository.findAllByJobRecommendationIdOrderByIdAsc(100L))
                .thenReturn(List.of());
        RecommendationQueryService service = new RecommendationQueryService(
                currentUserIdProvider,
                runRepository, recommendationRepository, detailRepository, skillRepository,
                new RecommendationSkillHighlightSelector()
        );

        var latestResult = service.getLatestPreferenceResult().orElseThrow();
        var latestDetail = service.getLatestDetailForJobPosting(200L).orElseThrow();

        assertEquals(10L, latestResult.run().runId());
        assertEquals(8L, latestResult.run().preference().industryId());
        assertEquals(1, latestResult.recommendations().size());
        assertEquals(10L, latestDetail.runId());
        assertEquals(100L, latestDetail.jobRecommendationId());
    }

    @Test
    void returnsEverySkillTypeAsASeparateGroupIncludingEmptyGroups() {
        RecommendationRunRepository runRepository = mock(RecommendationRunRepository.class);
        JobRecommendationRepository recommendationRepository = mock(JobRecommendationRepository.class);
        JobRecommendationSkillDetailRepository detailRepository =
                mock(JobRecommendationSkillDetailRepository.class);
        SkillRepository skillRepository = mock(SkillRepository.class);
        CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(2L);
        JobRecommendation recommendation = recommendation(posting());
        List<JobRecommendationSkillDetail> details = List.of(
                detail(10L, "TypeScript", JobPostingSkillType.REQUIRED, true, "1.0000"),
                detail(20L, "Vector DB", JobPostingSkillType.REQUIRED, false, "0.5000"),
                detail(30L, "AWS", JobPostingSkillType.RELATED, true, "0.7500")
        );
        when(recommendationRepository.findByIdAndRecommendationRunIdAndRecommendationRunUserId(
                100L, 10L, 2L
        )).thenReturn(Optional.of(recommendation));
        when(detailRepository.findAllByJobRecommendationIdOrderByIdAsc(100L)).thenReturn(details);
        RecommendationQueryService service = new RecommendationQueryService(
                currentUserIdProvider,
                runRepository, recommendationRepository, detailRepository, skillRepository,
                new RecommendationSkillHighlightSelector()
        );

        RecommendationDetailResponse response = service.getDetail(10L, 100L);

        assertEquals(List.of(
                        JobPostingSkillType.REQUIRED,
                        JobPostingSkillType.PREFERRED,
                        JobPostingSkillType.RELATED
                ),
                response.report().skillGroups().stream().map(group -> group.skillType()).toList());
        assertEquals(new BigDecimal("0.7500"), response.report().skillGroups().get(0).levelMatchRate());
        assertEquals(2, response.report().skillGroups().get(0).skills().size());
        assertTrue(response.report().skillGroups().get(1).skills().isEmpty());
        assertEquals(new BigDecimal("0.0000"), response.report().skillGroups().get(1).levelMatchRate());
        assertEquals(1, response.report().skillGroups().get(2).skills().size());
    }

    private JobRecommendation recommendation(JobPosting posting) {
        JobRecommendation value = mock(JobRecommendation.class);
        when(value.getId()).thenReturn(100L);
        when(value.getRankOrder()).thenReturn(1);
        when(value.getRecommendationGrade()).thenReturn(RecommendationGrade.RECOMMENDED);
        when(value.getTotalScore()).thenReturn(new BigDecimal("79.0100"));
        when(value.getReason()).thenReturn("지원 직무와 연결되는 역량을 보유하고 있습니다.");
        when(value.getJobPosting()).thenReturn(posting);
        return value;
    }

    private JobPosting posting() {
        Industry industry = mock(Industry.class);
        when(industry.getIndustryName()).thenReturn("IT");
        JobRole role = mock(JobRole.class);
        when(role.getJobRoleName()).thenReturn("백엔드 개발자");
        when(role.getIndustry()).thenReturn(industry);
        Company company = mock(Company.class);
        when(company.getCompanyName()).thenReturn("테스트 회사");
        when(company.getCompanySize()).thenReturn(CompanySize.STARTUP);
        JobPosting posting = mock(JobPosting.class);
        when(posting.getId()).thenReturn(200L);
        when(posting.getTitle()).thenReturn("AI 플랫폼 개발자");
        when(posting.getJobRole()).thenReturn(role);
        when(posting.getCompany()).thenReturn(company);
        return posting;
    }

    private JobRecommendationSkillDetail detail(
            Long id,
            String name,
            JobPostingSkillType type,
            boolean satisfied,
            String matchRate
    ) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        when(skill.getName()).thenReturn(name);
        JobRecommendationSkillDetail detail = mock(JobRecommendationSkillDetail.class);
        when(detail.getSkill()).thenReturn(skill);
        when(detail.getSkillType()).thenReturn(type);
        when(detail.isOwned()).thenReturn(satisfied);
        when(detail.isRequirementSatisfied()).thenReturn(satisfied);
        when(detail.getRequiredLevel()).thenReturn((short) 2);
        when(detail.getMatchRate()).thenReturn(new BigDecimal(matchRate));
        when(detail.getBaseMaxScore()).thenReturn(new BigDecimal("10.0000"));
        when(detail.getBaseContributionScore()).thenReturn(
                satisfied ? new BigDecimal("8.0000") : new BigDecimal("2.0000")
        );
        return detail;
    }
}
