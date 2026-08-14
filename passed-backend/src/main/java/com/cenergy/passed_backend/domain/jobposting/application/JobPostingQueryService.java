package com.cenergy.passed_backend.domain.jobposting.application;

import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingDetailResponse;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingCreateOptionsResponse;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingListRequest;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingListResponse;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingNamedOptionResponse;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingSummaryResponse;
import com.cenergy.passed_backend.domain.jobposting.entity.CompanySize;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.CompanyRepository;
import com.cenergy.passed_backend.domain.recommendation.repository.JobRecommendationRepository;
import com.cenergy.passed_backend.domain.skill.repository.SkillRepository;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class JobPostingQueryService {
    private final JobPostingRepository jobPostingRepository;
    private final JobRecommendationRepository recommendationRepository;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final CompanyRepository companyRepository;
    private final SkillRepository skillRepository;

    public JobPostingQueryService(
            JobPostingRepository jobPostingRepository,
            JobRecommendationRepository recommendationRepository,
            CurrentUserIdProvider currentUserIdProvider,
            CompanyRepository companyRepository,
            SkillRepository skillRepository
    ) {
        this.jobPostingRepository = jobPostingRepository;
        this.recommendationRepository = recommendationRepository;
        this.currentUserIdProvider = currentUserIdProvider;
        this.companyRepository = companyRepository;
        this.skillRepository = skillRepository;
    }

    public JobPostingCreateOptionsResponse findCreateOptions() {
        var companies = companyRepository.findAllNames().stream()
                .map(company -> new JobPostingNamedOptionResponse(
                        company.getId(), company.getName()
                ))
                .toList();
        var skills = skillRepository.findAllNames().stream()
                .map(skill -> new JobPostingNamedOptionResponse(
                        skill.getId(), skill.getName()
                ))
                .toList();
        return new JobPostingCreateOptionsResponse(companies, skills);
    }

    public JobPostingListResponse findAll(JobPostingListRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Long userId = currentUserIdProvider.getCurrentUserId();
        Page<JobPosting> postings = jobPostingRepository.findFiltered(
                queryText(request.keyword()), queryText(request.region()),
                queryId(request.industryId()), queryId(request.jobRoleId()),
                request.companySize() != null, queryCompanySize(request.companySize()),
                request.matchedOnly(), userId,
                PageRequest.of(request.page(), request.size(), Sort.by(Sort.Direction.DESC, "id"))
        );
        Set<Long> matchedIds = postings.isEmpty()
                ? Set.of()
                : Set.copyOf(recommendationRepository.findMatchedJobPostingIds(
                        userId,
                        postings.getContent().stream().map(JobPosting::getId).toList()
                ));
        return new JobPostingListResponse(
                postings.getContent().stream()
                        .map(posting -> toSummary(posting, matchedIds.contains(posting.getId())))
                        .toList(),
                postings.getNumber(),
                postings.getSize(),
                postings.getTotalElements(),
                postings.getTotalPages()
        );
    }

    private String queryText(String value) {
        return value == null ? "" : value;
    }

    private long queryId(Long value) {
        return value == null ? 0L : value;
    }

    private CompanySize queryCompanySize(CompanySize value) {
        return value == null ? CompanySize.STARTUP : value;
    }

    public JobPostingDetailResponse findById(Long jobPostingId) {
        if (jobPostingId == null || jobPostingId <= 0) {
            throw new JobPostingException(
                    ErrorCode.JOB_POSTING_INVALID_REQUEST,
                    "jobPostingId must be positive"
            );
        }
        return jobPostingRepository.findById(jobPostingId)
                .map(this::toDetail)
                .orElseThrow(() -> new JobPostingException(
                        ErrorCode.JOB_POSTING_NOT_FOUND,
                        "Job posting not found"
                ));
    }

    private JobPostingSummaryResponse toSummary(JobPosting posting, boolean matched) {
        return new JobPostingSummaryResponse(
                posting.getId(),
                posting.getTitle(),
                posting.getRegion(),
                posting.getCompany().getCompanyName(),
                posting.getCompany().getCompanySize().getLabel(),
                posting.getJobRole().getJobRoleName(),
                posting.getJobRole().getIndustry().getIndustryName(),
                matched
        );
    }

    private JobPostingDetailResponse toDetail(JobPosting posting) {
        return new JobPostingDetailResponse(
                posting.getId(),
                posting.getTitle(),
                posting.getJobRole().getIndustry().getIndustryName(),
                posting.getJobRole().getJobRoleName(),
                posting.getCompany().getCompanyName(),
                posting.getCompany().getCompanySize().getLabel(),
                posting.getRegion(),
                posting.getCareerType(),
                posting.getHireType(),
                posting.getEducationLevel(),
                posting.getPositionDetail(),
                posting.getMainDuty(),
                posting.getQualification(),
                posting.getPreference(),
                posting.getDisqualifyReason(),
                posting.getProcess(),
                posting.getCompany().getBenefits()
        );
    }
}
