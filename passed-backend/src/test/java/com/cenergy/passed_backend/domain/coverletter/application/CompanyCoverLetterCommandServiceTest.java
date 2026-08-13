package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemUpdateRequest;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyItemRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterCompanyRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterItemFeedbackRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        verify(feedbackRepository).deleteByCoverLetterCompanyItemId(10L);
    }
}
