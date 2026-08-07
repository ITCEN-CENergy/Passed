package com.cenergy.passed_backend.domain.coverletter.dto.responses;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 자기소개서 편집 화면에서 사용하는 공고별 자기소개서 상세 응답이다.
 * 문항은 서비스에서 displayOrder 오름차순으로 정렬해 전달한다.
 */
public record CompanyCoverLetterDetailResponse(
        Long id,
        Long jobPostingId,
        String companyName,
        String jobPostingTitle,
        String title,
        List<CompanyCoverLetterItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /** 부모 엔티티와 정렬된 문항 목록을 편집 화면용 응답으로 변환한다. */
    public static CompanyCoverLetterDetailResponse from(
            CoverLetterCompany coverLetter,
            List<CoverLetterCompanyItem> items
    ) {
        return new CompanyCoverLetterDetailResponse(
                coverLetter.getId(),
                coverLetter.getJobPosting().getId(),
                coverLetter.getJobPosting().getCompany().getCompanyName(),
                coverLetter.getJobPosting().getTitle(),
                coverLetter.getTitle(),
                items.stream().map(CompanyCoverLetterItemResponse::from).toList(),
                coverLetter.getCreatedAt(),
                coverLetter.getUpdatedAt()
        );
    }
}
