package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterDetailResponse;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterSummaryResponse;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyItemRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyRepository;
import com.cenergy.passed_backend.domain.roadmap.application.CurrentUserIdProvider;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 현재 사용자가 소유한 공고별 자기소개서를 조회하는 서비스다.
 * 모든 조회에 CurrentUserIdProvider의 사용자 ID를 결합해 다른 사용자의 문서를 숨긴다.
 */
@Service
@Transactional(readOnly = true)
public class CompanyCoverLetterQueryService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final CoverLetterCompanyRepository coverLetterRepository;
    private final CoverLetterCompanyItemRepository itemRepository;

    /** 조회에 필요한 인증 사용자 공급자와 저장소를 주입받는다. */
    public CompanyCoverLetterQueryService(
            CurrentUserIdProvider currentUserIdProvider,
            CoverLetterCompanyRepository coverLetterRepository,
            CoverLetterCompanyItemRepository itemRepository
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.coverLetterRepository = coverLetterRepository;
        this.itemRepository = itemRepository;
    }

    /** 현재 사용자의 공고별 자기소개서 목록을 최근 수정 순으로 반환한다. */
    public List<CompanyCoverLetterSummaryResponse> findAll() {
        return coverLetterRepository.findAllOwnedSummary(currentUserId()).stream()
                .map(CompanyCoverLetterSummaryResponse::from)
                .toList();
    }

    /** 현재 사용자가 소유한 한 건의 자기소개서와 정렬된 문항을 반환한다. */
    public CompanyCoverLetterDetailResponse findById(Long coverLetterId) {
        CoverLetterCompany coverLetter = findOwnedDetail(coverLetterId, currentUserId());
        List<CoverLetterCompanyItem> items = itemRepository
                .findAllByCoverLetterCompanyIdOrderByDisplayOrderAscIdAsc(coverLetter.getId());
        return CompanyCoverLetterDetailResponse.from(coverLetter, items);
    }

    /** 명령 서비스도 소유권 검증을 재사용할 수 있도록 상세 부모 엔티티를 제공한다. */
    CoverLetterCompany findOwnedDetail(Long coverLetterId, Long userId) {
        return coverLetterRepository.findOwnedDetail(coverLetterId, userId)
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_NOT_FOUND,
                        "Company cover letter not found"
                ));
    }

    /** 현재 사용자 ID가 없거나 유효하지 않으면 요청을 처리하지 않는다. */
    private Long currentUserId() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new CoverLetterException(
                    ErrorCode.COVER_LETTER_INVALID_REQUEST,
                    "Current user is required"
            );
        }
        return userId;
    }
}
