package com.cenergy.passed_backend.domain.coverletter.dto.responses;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;

import java.time.OffsetDateTime;

/**
 * 자기소개서 목록 화면에 필요한 공고별 자기소개서 요약 응답이다.
 */
public record CompanyCoverLetterSummaryResponse(
        Long id,
        Long jobPostingId,
        String companyName,
        String jobPostingTitle,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /** 공고와 기업이 로딩된 자기소개서 엔티티를 요약 응답으로 변환한다. */
    public static CompanyCoverLetterSummaryResponse from(CoverLetterCompany coverLetter) {
        boolean manual = coverLetter.isManual();
        return new CompanyCoverLetterSummaryResponse(
                coverLetter.getId(),
                manual ? null : coverLetter.getJobPosting().getId(),
                manual
                        ? coverLetter.getManualJobPosting().getCompanyName()
                        : coverLetter.getJobPosting().getCompany().getCompanyName(),
                manual
                        ? coverLetter.getManualJobPosting().getPostingTitle()
                        : coverLetter.getJobPosting().getTitle(),
                coverLetter.getTitle(),
                coverLetter.getCreatedAt(),
                coverLetter.getUpdatedAt()
        );
    }
}
