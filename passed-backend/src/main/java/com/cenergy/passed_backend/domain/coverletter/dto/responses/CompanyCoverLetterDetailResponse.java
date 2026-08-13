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
        OffsetDateTime updatedAt,
        boolean manual,
        CompanyCoverLetterJobPostingResponse jobPosting
) {
    /** 기존 호출부와의 소스 호환성을 유지하는 연결형 상세 응답 생성자다. */
    public CompanyCoverLetterDetailResponse(
            Long id,
            Long jobPostingId,
            String companyName,
            String jobPostingTitle,
            String title,
            List<CompanyCoverLetterItemResponse> items,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(id, jobPostingId, companyName, jobPostingTitle, title, items, createdAt, updatedAt,
                false, null);
    }

    /** 부모 엔티티와 정렬된 문항 목록을 편집 화면용 응답으로 변환한다. */
    public static CompanyCoverLetterDetailResponse from(
            CoverLetterCompany coverLetter,
            List<CoverLetterCompanyItem> items
    ) {
        CompanyCoverLetterJobPostingResponse posting = CompanyCoverLetterJobPostingResponse.from(coverLetter);
        return new CompanyCoverLetterDetailResponse(
                coverLetter.getId(),
                posting.id(),
                posting.companyName(),
                posting.postingTitle(),
                coverLetter.getTitle(),
                items.stream().map(CompanyCoverLetterItemResponse::from).toList(),
                coverLetter.getCreatedAt(),
                coverLetter.getUpdatedAt(),
                coverLetter.isManual(),
                posting
        );
    }
}
