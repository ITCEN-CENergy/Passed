package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.dto.requests.CommonCoverLetterUpsertRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CommonCoverLetterResponse;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CoverLetterQuestionResponse;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetter;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterItem;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterQuestion;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterItemRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterQuestionRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterRepository;
import com.cenergy.passed_backend.domain.roadmap.application.CurrentUserIdProvider;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 스킬 추출 파이프라인이 읽는 공통 자기소개서 테이블만 관리한다.
 */
@Service
public class CommonCoverLetterService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final UserRepository userRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final CoverLetterItemRepository itemRepository;
    private final CoverLetterQuestionRepository questionRepository;

    public CommonCoverLetterService(
            CurrentUserIdProvider currentUserIdProvider,
            UserRepository userRepository,
            CoverLetterRepository coverLetterRepository,
            CoverLetterItemRepository itemRepository,
            CoverLetterQuestionRepository questionRepository
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.userRepository = userRepository;
        this.coverLetterRepository = coverLetterRepository;
        this.itemRepository = itemRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public List<CoverLetterQuestionResponse> findActiveQuestions() {
        return questionRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(CoverLetterQuestionResponse::from)
                .toList();
    }

    @Transactional
    public CommonCoverLetterResponse create(CommonCoverLetterUpsertRequest request) {
        Long userId = currentUserId();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> error(ErrorCode.COVER_LETTER_USER_NOT_FOUND, "Current user not found"));
        if (coverLetterRepository.existsByUserId(userId)) {
            throw error(ErrorCode.COVER_LETTER_ALREADY_EXISTS, "Common cover letter already exists");
        }

        Map<Long, CoverLetterQuestion> questions = loadQuestions(request);
        CoverLetter coverLetter = coverLetterRepository.save(CoverLetter.create(user));
        List<CoverLetterItem> items = request.items().stream()
                .map(value -> CoverLetterItem.create(coverLetter, questions.get(value.questionId()), value.answer()))
                .toList();
        return CommonCoverLetterResponse.from(coverLetter, itemRepository.saveAll(items));
    }

    @Transactional(readOnly = true)
    public CommonCoverLetterResponse findMine() {
        CoverLetter coverLetter = coverLetterRepository.findByUserId(currentUserId())
                .orElseThrow(() -> error(ErrorCode.COVER_LETTER_NOT_FOUND, "Common cover letter not found"));
        return response(coverLetter);
    }

    @Transactional
    public CommonCoverLetterResponse update(CommonCoverLetterUpsertRequest request) {
        CoverLetter coverLetter = coverLetterRepository.findByUserIdForUpdate(currentUserId())
                .orElseThrow(() -> error(ErrorCode.COVER_LETTER_NOT_FOUND, "Common cover letter not found"));
        Map<Long, CoverLetterQuestion> questions = loadQuestions(request);
        Map<Long, CoverLetterItem> remaining = new HashMap<>();
        itemRepository.findAllByCoverLetterIdOrderByQuestion_DisplayOrderAscQuestion_IdAsc(coverLetter.getId())
                .forEach(item -> remaining.put(item.getQuestion().getId(), item));

        List<CoverLetterItem> current = new ArrayList<>();
        for (CommonCoverLetterUpsertRequest.Item value : request.items()) {
            CoverLetterItem item = remaining.remove(value.questionId());
            if (item == null) {
                item = CoverLetterItem.create(coverLetter, questions.get(value.questionId()), value.answer());
            } else {
                item.updateAnswer(value.answer());
            }
            current.add(item);
        }
        itemRepository.deleteAll(remaining.values());
        itemRepository.saveAll(current);
        return response(coverLetter);
    }

    @Transactional
    public void deleteMine() {
        CoverLetter coverLetter = coverLetterRepository.findByUserIdForUpdate(currentUserId())
                .orElseThrow(() -> error(ErrorCode.COVER_LETTER_NOT_FOUND, "Common cover letter not found"));
        coverLetterRepository.delete(coverLetter);
    }

    private Map<Long, CoverLetterQuestion> loadQuestions(CommonCoverLetterUpsertRequest request) {
        /* 비활성 질문이나 중복 질문을 저장하면 고정 질문 화면과 분석 입력이 어긋난다. */
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw error(ErrorCode.COVER_LETTER_INVALID_REQUEST, "At least one answer is required");
        }
        Set<Long> ids = new HashSet<>();
        for (CommonCoverLetterUpsertRequest.Item item : request.items()) {
            if (item == null || item.questionId() == null || !ids.add(item.questionId())) {
                throw error(ErrorCode.COVER_LETTER_INVALID_REQUEST,
                        "Question ids must be present and unique");
            }
        }
        List<CoverLetterQuestion> questions = questionRepository.findAllByIdInAndActiveTrue(ids);
        if (questions.size() != ids.size()) {
            throw error(ErrorCode.COVER_LETTER_INVALID_REQUEST,
                    "Every question must exist and be active");
        }
        Map<Long, CoverLetterQuestion> byId = new HashMap<>();
        questions.forEach(question -> byId.put(question.getId(), question));
        return byId;
    }

    private CommonCoverLetterResponse response(CoverLetter coverLetter) {
        return CommonCoverLetterResponse.from(coverLetter,
                itemRepository.findAllByCoverLetterIdOrderByQuestion_DisplayOrderAscQuestion_IdAsc(
                        coverLetter.getId()));
    }

    private Long currentUserId() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw error(ErrorCode.COVER_LETTER_INVALID_REQUEST, "Current user is required");
        }
        return userId;
    }

    private CoverLetterException error(ErrorCode code, String message) {
        return new CoverLetterException(code, message);
    }
}
