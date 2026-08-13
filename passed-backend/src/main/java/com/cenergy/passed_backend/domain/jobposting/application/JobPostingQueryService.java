package com.cenergy.passed_backend.domain.jobposting.application;

import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingDetailResponse;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingListRequest;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingListResponse;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingSummaryResponse;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class JobPostingQueryService {
    private final JobPostingRepository jobPostingRepository;

    public JobPostingQueryService(JobPostingRepository jobPostingRepository) {
        this.jobPostingRepository = jobPostingRepository;
    }

    public JobPostingListResponse findAll(JobPostingListRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Page<JobPosting> postings = jobPostingRepository.findAll(PageRequest.of(
                request.page(),
                request.size(),
                Sort.by(Sort.Direction.DESC, "id")
        ));
        return new JobPostingListResponse(
                postings.getContent().stream().map(this::toSummary).toList(),
                postings.getNumber(),
                postings.getSize(),
                postings.getTotalElements(),
                postings.getTotalPages()
        );
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

    private JobPostingSummaryResponse toSummary(JobPosting posting) {
        return new JobPostingSummaryResponse(
                posting.getId(),
                posting.getTitle(),
                posting.getRegion(),
                posting.getCompany().getCompanyName(),
                posting.getJobRole().getJobRoleName(),
                posting.getJobRole().getIndustry().getIndustryName()
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
