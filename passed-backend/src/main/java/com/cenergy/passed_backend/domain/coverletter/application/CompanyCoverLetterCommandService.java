package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemUpdateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterUpdateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterDetailResponse;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterItemResponse;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyItemRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterItemFeedbackRepository;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.domain.roadmap.application.CurrentUserIdProvider;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 공고별 자기소개서와 문항의 생성·수정·삭제를 수행하는 트랜잭션 서비스다.
 * 사용자 ID는 요청 본문이 아닌 CurrentUserIdProvider에서만 읽어 소유권을 강제한다.
 */
@Service
public class CompanyCoverLetterCommandService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final UserRepository userRepository;
    private final JobPostingRepository jobPostingRepository;
    private final CoverLetterCompanyRepository coverLetterRepository;
    private final CoverLetterCompanyItemRepository itemRepository;
    private final CoverLetterItemFeedbackRepository itemFeedbackRepository;
    private final CompanyCoverLetterQueryService queryService;

    /** 명령 처리에 필요한 현재 사용자 공급자, 저장소, 조회 서비스를 주입받는다. */
    public CompanyCoverLetterCommandService(
            CurrentUserIdProvider currentUserIdProvider,
            UserRepository userRepository,
            JobPostingRepository jobPostingRepository,
            CoverLetterCompanyRepository coverLetterRepository,
            CoverLetterCompanyItemRepository itemRepository,
            CoverLetterItemFeedbackRepository itemFeedbackRepository,
            CompanyCoverLetterQueryService queryService
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.userRepository = userRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.coverLetterRepository = coverLetterRepository;
        this.itemRepository = itemRepository;
        this.itemFeedbackRepository = itemFeedbackRepository;
        this.queryService = queryService;
    }

    /**
     * 현재 사용자의 새 공고별 자기소개서와 최초 문항 목록을 하나의 트랜잭션으로 저장한다.
     * 같은 공고에 기존 자기소개서가 있으면 유일 제약 위반 전에 명시적으로 충돌을 반환한다.
     */
    @Transactional
    public CompanyCoverLetterDetailResponse create(CompanyCoverLetterCreateRequest request) {
        Long userId = currentUserId();
        validateDistinctDisplayOrders(request.items());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_USER_NOT_FOUND,
                        "Current user not found"
                ));
        JobPosting jobPosting = jobPostingRepository.findById(request.jobPostingId())
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_JOB_POSTING_NOT_FOUND,
                        "Job posting not found"
                ));
        if (coverLetterRepository.existsByUserIdAndJobPostingId(userId, jobPosting.getId())) {
            throw new CoverLetterException(
                    ErrorCode.COVER_LETTER_ALREADY_EXISTS,
                    "A company cover letter already exists for this job posting"
            );
        }

        CoverLetterCompany coverLetter = coverLetterRepository.save(
                CoverLetterCompany.create(user, jobPosting, request.title())
        );
        List<CoverLetterCompanyItem> items = request.items().stream()
                .map(item -> toEntity(coverLetter, item))
                .toList();
        itemRepository.saveAll(items);
        return CompanyCoverLetterDetailResponse.from(coverLetter, items);
    }

    /** 현재 사용자가 소유한 자기소개서 제목을 수정한 뒤 최신 상세 응답을 반환한다. */
    @Transactional
    public CompanyCoverLetterDetailResponse updateTitle(
            Long coverLetterId,
            CompanyCoverLetterUpdateRequest request
    ) {
        CoverLetterCompany coverLetter = findOwnedForUpdate(coverLetterId, currentUserId());
        coverLetter.updateTitle(request.title());
        return queryService.findById(coverLetter.getId());
    }

    /** 현재 사용자가 소유한 자기소개서에 한 문항을 추가한다. */
    @Transactional
    public CompanyCoverLetterItemResponse addItem(
            Long coverLetterId,
            CompanyCoverLetterItemCreateRequest request
    ) {
        CoverLetterCompany coverLetter = findOwnedForUpdate(coverLetterId, currentUserId());
        rejectDuplicateDisplayOrder(coverLetter.getId(), request.displayOrder(), null);
        CoverLetterCompanyItem saved = itemRepository.save(toEntity(coverLetter, request));
        return CompanyCoverLetterItemResponse.from(saved);
    }

    /**
     * 현재 사용자가 소유한 문항을 수정한다.
     * 답변이 달라지면 이전 답변 기반의 첨삭 결과를 함께 삭제해 stale 결과를 막는다.
     */
    @Transactional
    public CompanyCoverLetterItemResponse updateItem(
            Long itemId,
            CompanyCoverLetterItemUpdateRequest request
    ) {
        CoverLetterCompanyItem item = itemRepository.findOwnedItemForUpdate(itemId, currentUserId())
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_ITEM_NOT_FOUND,
                        "Company cover letter item not found"
                ));
        rejectDuplicateDisplayOrder(
                item.getCoverLetterCompany().getId(), request.displayOrder(), item.getId()
        );
        boolean answerChanged = !Objects.equals(item.getAnswer(), request.answer());
        item.update(
                request.questionText(),
                request.answer(),
                request.characterLimit(),
                request.displayOrder()
        );
        if (answerChanged) {
            itemFeedbackRepository.deleteByCoverLetterCompanyItemId(item.getId());
        }
        return CompanyCoverLetterItemResponse.from(item);
    }

    /**
     * 현재 사용자가 소유한 자기소개서를 삭제한다.
     * DB FK의 ON DELETE CASCADE가 하위 문항과 그 첨삭 결과를 함께 삭제한다.
     */
    @Transactional
    public void delete(Long coverLetterId) {
        CoverLetterCompany coverLetter = findOwnedForUpdate(coverLetterId, currentUserId());
        coverLetterRepository.delete(coverLetter);
    }

    /**
     * 현재 사용자가 소유한 한 문항을 삭제한다.
     * DB FK의 ON DELETE CASCADE가 연결된 문항 첨삭 결과를 함께 삭제한다.
     */
    @Transactional
    public void deleteItem(Long itemId) {
        CoverLetterCompanyItem item = itemRepository.findOwnedItemForUpdate(itemId, currentUserId())
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_ITEM_NOT_FOUND,
                        "Company cover letter item not found"
                ));
        itemRepository.delete(item);
    }

    /** 생성 요청의 DTO를 실제 문항 엔티티로 변환한다. */
    private static CoverLetterCompanyItem toEntity(
            CoverLetterCompany coverLetter,
            CompanyCoverLetterItemCreateRequest request
    ) {
        return CoverLetterCompanyItem.create(
                coverLetter,
                request.questionText(),
                request.answer(),
                request.characterLimit(),
                request.displayOrder()
        );
    }

    /** 생성 시 요청 본문 안에서 중복되는 문항 순서를 미리 검증한다. */
    private static void validateDistinctDisplayOrders(List<CompanyCoverLetterItemCreateRequest> items) {
        Set<Integer> displayOrders = new HashSet<>();
        for (CompanyCoverLetterItemCreateRequest item : items) {
            if (!displayOrders.add(item.displayOrder())) {
                throw new CoverLetterException(
                        ErrorCode.COVER_LETTER_DISPLAY_ORDER_CONFLICT,
                        "Duplicate displayOrder in request"
                );
            }
        }
    }

    /** 기존 문항과 충돌하는 displayOrder가 있으면 명시적인 409 오류를 만든다. */
    private void rejectDuplicateDisplayOrder(Long coverLetterId, int displayOrder, Long currentItemId) {
        boolean duplicate = currentItemId == null
                ? itemRepository.existsByCoverLetterCompanyIdAndDisplayOrder(coverLetterId, displayOrder)
                : itemRepository.existsByCoverLetterCompanyIdAndDisplayOrderAndIdNot(
                        coverLetterId, displayOrder, currentItemId
                );
        if (duplicate) {
            throw new CoverLetterException(
                    ErrorCode.COVER_LETTER_DISPLAY_ORDER_CONFLICT,
                    "Duplicate displayOrder in company cover letter"
            );
        }
    }

    /** 잠금 조회로 현재 사용자의 부모 자기소개서만 수정·삭제할 수 있게 한다. */
    private CoverLetterCompany findOwnedForUpdate(Long coverLetterId, Long userId) {
        return coverLetterRepository.findOwnedForUpdate(coverLetterId, userId)
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_NOT_FOUND,
                        "Company cover letter not found"
                ));
    }

    /** 현재 사용자 ID가 없거나 유효하지 않으면 쓰기 작업을 시작하지 않는다. */
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
