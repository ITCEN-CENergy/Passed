package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemReplaceRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemUpdateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterReplaceRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterUpdateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.ManualCompanyCoverLetterCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.ManualJobPostingRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterDetailResponse;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterItemResponse;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterManualJobPosting;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyItemRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterItemFeedbackRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterFeedbackRepository;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공고별 자기소개서와 문항의 생성·수정·삭제를 수행하는 트랜잭션 서비스다.
 * 사용자 ID는 요청 본문이 아닌 CurrentUserIdProvider에서만 읽어 소유권을 강제한다.
 */
@Service
public class CompanyCoverLetterCommandService {
    private static final int MAX_ITEM_COUNT = 30;
    private static final Pattern NUMBERED_TITLE_PATTERN = Pattern.compile("^자기소개서 (\\d+)$");

    private final CurrentUserIdProvider currentUserIdProvider;
    private final UserRepository userRepository;
    private final JobPostingRepository jobPostingRepository;
    private final CoverLetterCompanyRepository coverLetterRepository;
    private final CoverLetterCompanyItemRepository itemRepository;
    private final CoverLetterItemFeedbackRepository itemFeedbackRepository;
    private final CoverLetterFeedbackRepository overallFeedbackRepository;
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
        this(currentUserIdProvider, userRepository, jobPostingRepository, coverLetterRepository,
                itemRepository, itemFeedbackRepository, queryService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CompanyCoverLetterCommandService(
            CurrentUserIdProvider currentUserIdProvider,
            UserRepository userRepository,
            JobPostingRepository jobPostingRepository,
            CoverLetterCompanyRepository coverLetterRepository,
            CoverLetterCompanyItemRepository itemRepository,
            CoverLetterItemFeedbackRepository itemFeedbackRepository,
            CompanyCoverLetterQueryService queryService,
            CoverLetterFeedbackRepository overallFeedbackRepository
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.userRepository = userRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.coverLetterRepository = coverLetterRepository;
        this.itemRepository = itemRepository;
        this.itemFeedbackRepository = itemFeedbackRepository;
        this.queryService = queryService;
        this.overallFeedbackRepository = overallFeedbackRepository;
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
        itemRepository.saveAllAndFlush(items);
        return queryService.findById(coverLetter.getId());
    }

    /** 직접 입력한 공고 스냅샷과 최초 자기소개서 문항을 함께 저장한다. */
    @Transactional
    public CompanyCoverLetterDetailResponse createManual(ManualCompanyCoverLetterCreateRequest request) {
        validateDistinctDisplayOrders(request.items());
        Long userId = currentUserId();
        boolean needsNumberedTitle = isBlank(request.title()) && isBlank(request.jobPosting().companyName());
        User user = (needsNumberedTitle
                ? userRepository.findByIdForUpdate(userId)
                : userRepository.findById(userId))
                .orElseThrow(() -> new CoverLetterException(
                        ErrorCode.COVER_LETTER_USER_NOT_FOUND,
                        "Current user not found"
                ));
        CoverLetterManualJobPosting posting = manualPosting(request.jobPosting());
        String title = resolveManualTitle(request.title(), posting.getCompanyName(), userId);
        CoverLetterCompany coverLetter = coverLetterRepository.save(
                CoverLetterCompany.createManual(user, posting, title)
        );
        List<CoverLetterCompanyItem> items = request.items().stream()
                .map(item -> toEntity(coverLetter, item))
                .toList();
        itemRepository.saveAllAndFlush(items);
        return queryService.findById(coverLetter.getId());
    }

    /** 편집 화면의 제목, 직접 입력 공고, 전체 문항 목록을 한 트랜잭션으로 동기화한다. */
    @Transactional
    public CompanyCoverLetterDetailResponse replace(
            Long coverLetterId,
            CompanyCoverLetterReplaceRequest request
    ) {
        Long userId = currentUserId();
        CoverLetterCompany coverLetter = findOwnedForUpdate(coverLetterId, userId);
        validateReplaceRequest(request);
        boolean postingChanged = updatePosting(coverLetter, request.jobPosting());

        List<CoverLetterCompanyItem> existingItems = itemRepository
                .findAllByCoverLetterCompanyIdOrderByDisplayOrderAscIdAsc(coverLetterId);
        Map<Long, CoverLetterCompanyItem> existingById = new HashMap<>();
        for (int index = 0; index < existingItems.size(); index++) {
            CoverLetterCompanyItem item = existingItems.get(index);
            existingById.put(item.getId(), item);
            item.prepareDisplayOrder(1_000_000 + index);
        }
        itemRepository.flush();

        Set<Long> retainedIds = new HashSet<>();
        request.items().forEach(itemRequest ->
                replaceItem(coverLetter, existingById, retainedIds, itemRequest));
        List<CoverLetterCompanyItem> removedItems = existingItems.stream()
                .filter(item -> !retainedIds.contains(item.getId()))
                .toList();
        itemRepository.deleteAll(removedItems);

        if (postingChanged) {
            itemFeedbackRepository.deleteByCoverLetterCompanyItemCoverLetterCompanyId(coverLetterId);
        }
        deleteOverallFeedback(coverLetterId);
        coverLetter.updateTitle(request.title());
        coverLetter.markItemsChanged();
        itemRepository.flush();
        return queryService.findById(coverLetter.getId());
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
        if (itemRepository.countByCoverLetterCompanyId(coverLetterId) >= MAX_ITEM_COUNT) {
            throw new CoverLetterException(
                    ErrorCode.COVER_LETTER_INVALID_REQUEST,
                    "A company cover letter can contain at most 30 items"
            );
        }
        rejectDuplicateDisplayOrder(coverLetter.getId(), request.displayOrder(), null);
        CoverLetterCompanyItem saved = itemRepository.save(toEntity(coverLetter, request));
        deleteOverallFeedback(coverLetterId);
        coverLetter.markItemsChanged();
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
        boolean feedbackInputChanged = !Objects.equals(item.getQuestionText(), request.questionText().trim())
                || !Objects.equals(item.getAnswer(), request.answer());
        item.update(
                request.questionText(),
                request.answer(),
                request.characterLimit(),
                request.displayOrder()
        );
        item.getCoverLetterCompany().markItemsChanged();
        if (feedbackInputChanged) {
            itemFeedbackRepository.deleteByCoverLetterCompanyItemId(item.getId());
            deleteOverallFeedback(item.getCoverLetterCompany().getId());
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
        item.getCoverLetterCompany().markItemsChanged();
        deleteOverallFeedback(item.getCoverLetterCompany().getId());
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

    private static CoverLetterManualJobPosting manualPosting(ManualJobPostingRequest request) {
        return CoverLetterManualJobPosting.create(
                request.postingTitle(), request.companyName(), request.jobRoleName(),
                request.positionDetail(), request.careerType(), request.hireType(), request.mainDuty(),
                request.qualification(), request.preference()
        );
    }

    private boolean updatePosting(CoverLetterCompany coverLetter, ManualJobPostingRequest request) {
        if (coverLetter.isManual()) {
            if (request == null) {
                throw new CoverLetterException(
                        ErrorCode.COVER_LETTER_INVALID_REQUEST,
                        "Manual job posting is required"
                );
            }
            return coverLetter.updateManualJobPosting(
                    request.postingTitle(), request.companyName(), request.jobRoleName(),
                    request.positionDetail(), request.careerType(), request.hireType(), request.mainDuty(),
                    request.qualification(), request.preference()
            );
        }
        if (request != null) {
            throw new CoverLetterException(
                    ErrorCode.COVER_LETTER_INVALID_REQUEST,
                    "Linked job posting cannot be edited from cover letter"
            );
        }
        return false;
    }

    private CoverLetterCompanyItem replaceItem(
            CoverLetterCompany coverLetter,
            Map<Long, CoverLetterCompanyItem> existingById,
            Set<Long> retainedIds,
            CompanyCoverLetterItemReplaceRequest request
    ) {
        if (request.id() == null) {
            return itemRepository.save(CoverLetterCompanyItem.create(
                    coverLetter, request.questionText(), request.answer(),
                    request.characterLimit(), request.displayOrder()
            ));
        }
        CoverLetterCompanyItem item = existingById.get(request.id());
        if (item == null || !retainedIds.add(request.id())) {
            throw new CoverLetterException(
                    ErrorCode.COVER_LETTER_INVALID_REQUEST,
                    "Item must belong to the edited cover letter and appear once"
            );
        }
        boolean feedbackInputChanged = !Objects.equals(item.getQuestionText(), request.questionText().trim())
                || !Objects.equals(item.getAnswer(), request.answer());
        item.update(request.questionText(), request.answer(), request.characterLimit(), request.displayOrder());
        if (feedbackInputChanged) {
            itemFeedbackRepository.deleteByCoverLetterCompanyItemId(item.getId());
            deleteOverallFeedback(coverLetter.getId());
        }
        return item;
    }

    private void deleteOverallFeedback(Long coverLetterId) {
        if (overallFeedbackRepository != null) {
            overallFeedbackRepository.deleteByCoverLetterCompanyId(coverLetterId);
        }
    }

    private static void validateReplaceRequest(CompanyCoverLetterReplaceRequest request) {
        validateItemCount(request.items().size());
        Set<Integer> displayOrders = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        for (CompanyCoverLetterItemReplaceRequest item : request.items()) {
            if (!displayOrders.add(item.displayOrder())) {
                throw new CoverLetterException(
                        ErrorCode.COVER_LETTER_DISPLAY_ORDER_CONFLICT,
                        "Duplicate displayOrder in request"
                );
            }
            if (item.id() != null && !ids.add(item.id())) {
                throw new CoverLetterException(
                        ErrorCode.COVER_LETTER_INVALID_REQUEST,
                        "Duplicate item id in request"
                );
            }
        }
    }

    /** 생성 시 요청 본문 안에서 중복되는 문항 순서를 미리 검증한다. */
    private static void validateDistinctDisplayOrders(List<CompanyCoverLetterItemCreateRequest> items) {
        validateItemCount(items.size());
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

    /** 제목을 생략하면 회사명을 사용하고, 회사명도 없으면 사용자별 다음 번호를 사용한다. */
    private String resolveManualTitle(String requestedTitle, String companyName, Long userId) {
        if (!isBlank(requestedTitle)) {
            return requestedTitle.trim();
        }
        if (!isBlank(companyName)) {
            return companyName.trim();
        }
        long maxNumber = 0;
        for (String title : coverLetterRepository.findNumberedTitles(userId)) {
            Matcher matcher = NUMBERED_TITLE_PATTERN.matcher(title);
            if (matcher.matches()) {
                try {
                    maxNumber = Math.max(maxNumber, Long.parseLong(matcher.group(1)));
                } catch (NumberFormatException ignored) {
                    // Long 범위를 벗어난 비정상 제목은 다음 번호 계산에서 제외한다.
                }
            }
        }
        return "자기소개서 " + (maxNumber + 1);
    }

    private static void validateItemCount(int itemCount) {
        if (itemCount > MAX_ITEM_COUNT) {
            throw new CoverLetterException(
                    ErrorCode.COVER_LETTER_INVALID_REQUEST,
                    "A company cover letter can contain at most 30 items"
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
