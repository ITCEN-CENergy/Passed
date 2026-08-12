package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemUpdateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemReplaceRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterReplaceRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.ManualJobPostingRequest;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterManualJobPosting;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyItemRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterItemFeedbackRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Verifies command-side business rules that do not require a database.
 */
class CompanyCoverLetterCommandServiceTest {

    /**
     * A changed answer invalidates the feedback generated from the older answer.
     * This prevents the client from displaying an AI result that no longer matches its source text.
     */
    @Test
    void deletesExistingFeedbackWhenAnswerChanges() {
        CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
        UserRepository userRepository = mock(UserRepository.class);
        JobPostingRepository jobPostingRepository = mock(JobPostingRepository.class);
        CoverLetterCompanyRepository coverLetterRepository = mock(CoverLetterCompanyRepository.class);
        CoverLetterCompanyItemRepository itemRepository = mock(CoverLetterCompanyItemRepository.class);
        CoverLetterItemFeedbackRepository feedbackRepository = mock(CoverLetterItemFeedbackRepository.class);
        CompanyCoverLetterQueryService queryService = mock(CompanyCoverLetterQueryService.class);
        CoverLetterCompanyItem item = mock(CoverLetterCompanyItem.class);
        CoverLetterCompany coverLetter = mock(CoverLetterCompany.class);
        CompanyCoverLetterItemUpdateRequest request = new CompanyCoverLetterItemUpdateRequest(
                "updated question", "updated answer", 1000, 1
        );

        when(currentUserIdProvider.getCurrentUserId()).thenReturn(257L);
        when(itemRepository.findOwnedItemForUpdate(10L, 257L)).thenReturn(Optional.of(item));
        when(item.getCoverLetterCompany()).thenReturn(coverLetter);
        when(coverLetter.getId()).thenReturn(3L);
        when(item.getId()).thenReturn(10L);
        when(item.getAnswer()).thenReturn("old answer");
        when(item.getQuestionText()).thenReturn("updated question");
        when(item.getCharacterLimit()).thenReturn(1000);
        when(item.getDisplayOrder()).thenReturn(1);

        CompanyCoverLetterCommandService service = new CompanyCoverLetterCommandService(
                currentUserIdProvider,
                userRepository,
                jobPostingRepository,
                coverLetterRepository,
                itemRepository,
                feedbackRepository,
                queryService
        );

        service.updateItem(10L, request);

        verify(item).update("updated question", "updated answer", 1000, 1);
        verify(coverLetter).markItemsChanged();
        verify(feedbackRepository).deleteByCoverLetterCompanyItemId(10L);
    }

    @Test
    void replacesManualPostingAndItemsAndInvalidatesAffectedFeedback() {
        CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
        UserRepository userRepository = mock(UserRepository.class);
        JobPostingRepository jobPostingRepository = mock(JobPostingRepository.class);
        CoverLetterCompanyRepository coverLetterRepository = mock(CoverLetterCompanyRepository.class);
        CoverLetterCompanyItemRepository itemRepository = mock(CoverLetterCompanyItemRepository.class);
        CoverLetterItemFeedbackRepository feedbackRepository = mock(CoverLetterItemFeedbackRepository.class);
        CompanyCoverLetterQueryService queryService = mock(CompanyCoverLetterQueryService.class);
        CoverLetterCompany coverLetter = CoverLetterCompany.createManual(
                mock(com.cenergy.passed_backend.domain.user.entity.User.class),
                CoverLetterManualJobPosting.create(
                        "이전 공고", "기업", "개발자", null, null, null,
                        "이전 업무", "이전 자격", null
                ),
                "이전 자기소개서"
        );
        CoverLetterCompanyItem item = mock(CoverLetterCompanyItem.class);
        when(item.getId()).thenReturn(10L);
        when(item.getQuestionText()).thenReturn("이전 질문");
        when(item.getAnswer()).thenReturn("이전 답변");
        when(item.getCharacterLimit()).thenReturn(1000);
        when(item.getDisplayOrder()).thenReturn(1);
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(257L);
        when(coverLetterRepository.findOwnedForUpdate(3L, 257L)).thenReturn(Optional.of(coverLetter));
        when(itemRepository.findAllByCoverLetterCompanyIdOrderByDisplayOrderAscIdAsc(3L))
                .thenReturn(List.of(item));
        CompanyCoverLetterCommandService service = new CompanyCoverLetterCommandService(
                currentUserIdProvider, userRepository, jobPostingRepository, coverLetterRepository,
                itemRepository, feedbackRepository, queryService
        );
        CompanyCoverLetterReplaceRequest request = new CompanyCoverLetterReplaceRequest(
                "수정 자기소개서",
                new ManualJobPostingRequest(
                        "수정 공고", "기업", "개발자", null, "신입", "정규직",
                        "수정 업무", "수정 자격", "우대"
                ),
                List.of(new CompanyCoverLetterItemReplaceRequest(
                        10L, "수정 질문", "수정 답변", 1000, 1
                ))
        );

        service.replace(3L, request);

        verify(item).prepareDisplayOrder(1_000_000);
        verify(item).update("수정 질문", "수정 답변", 1000, 1);
        verify(feedbackRepository).deleteByCoverLetterCompanyItemId(10L);
        verify(feedbackRepository).deleteByCoverLetterCompanyItemCoverLetterCompanyId(3L);
        verify(itemRepository, times(2)).flush();
    }
}
