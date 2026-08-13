package com.cenergy.passed_backend.domain.user.application;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetter;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterItemRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterRepository;
import com.cenergy.passed_backend.domain.resume.entity.PersonalInfo;
import com.cenergy.passed_backend.domain.resume.entity.Resume;
import com.cenergy.passed_backend.domain.resume.repository.PersonalInfoRepository;
import com.cenergy.passed_backend.domain.resume.repository.ResumeRepository;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyPageQueryServiceTest {
    private CurrentUserIdProvider currentUserIdProvider;
    private UserRepository userRepository;
    private ResumeRepository resumeRepository;
    private PersonalInfoRepository personalInfoRepository;
    private CoverLetterRepository coverLetterRepository;
    private CoverLetterItemRepository coverLetterItemRepository;
    private MyPageQueryService service;

    @BeforeEach
    void setUp() {
        currentUserIdProvider = mock(CurrentUserIdProvider.class);
        userRepository = mock(UserRepository.class);
        resumeRepository = mock(ResumeRepository.class);
        personalInfoRepository = mock(PersonalInfoRepository.class);
        coverLetterRepository = mock(CoverLetterRepository.class);
        coverLetterItemRepository = mock(CoverLetterItemRepository.class);
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(56L);
        service = new MyPageQueryService(
                currentUserIdProvider,
                userRepository,
                resumeRepository,
                personalInfoRepository,
                coverLetterRepository,
                coverLetterItemRepository
        );
    }

    @Test
    void returnsCurrentUserProfileAndDocumentDates() {
        User user = mock(User.class);
        Resume resume = mock(Resume.class);
        PersonalInfo personalInfo = mock(PersonalInfo.class);
        CoverLetter coverLetter = mock(CoverLetter.class);
        OffsetDateTime resumeDate = OffsetDateTime.parse("2026-08-10T15:20:00+09:00");
        OffsetDateTime coverLetterDate = OffsetDateTime.parse("2026-08-12T18:10:00+09:00");

        when(user.getName()).thenReturn("김민주");
        when(user.getEmail()).thenReturn("kimminju@example.com");
        when(userRepository.findById(56L)).thenReturn(Optional.of(user));
        when(resumeRepository.findByUserId(56L)).thenReturn(Optional.of(resume));
        when(resume.getId()).thenReturn(3L);
        when(resume.getCreatedAt()).thenReturn(resumeDate);
        when(personalInfoRepository.findByResumeId(3L)).thenReturn(Optional.of(personalInfo));
        when(personalInfo.getPhotoUrl()).thenReturn("/uploads/resume-photos/profile.png");
        when(coverLetterRepository.findByUserId(56L)).thenReturn(Optional.of(coverLetter));
        when(coverLetter.getId()).thenReturn(7L);
        when(coverLetterItemRepository.findLatestUpdatedAtByCoverLetterId(7L))
                .thenReturn(Optional.of(coverLetterDate));

        var response = service.findMine();

        assertEquals("김민주", response.name());
        assertEquals("kimminju@example.com", response.email());
        assertEquals("/uploads/resume-photos/profile.png", response.profileImageUrl());
        assertEquals(resumeDate, response.resumeUpdatedAt());
        assertEquals(coverLetterDate, response.coverLetterUpdatedAt());
    }

    @Test
    void returnsNullDocumentValuesWhenCurrentUserHasNoDocuments() {
        User user = mock(User.class);
        when(user.getName()).thenReturn("김민주");
        when(user.getEmail()).thenReturn("kimminju@example.com");
        when(userRepository.findById(56L)).thenReturn(Optional.of(user));
        when(resumeRepository.findByUserId(56L)).thenReturn(Optional.empty());
        when(coverLetterRepository.findByUserId(56L)).thenReturn(Optional.empty());

        var response = service.findMine();

        assertNull(response.profileImageUrl());
        assertNull(response.resumeUpdatedAt());
        assertNull(response.coverLetterUpdatedAt());
    }

    @Test
    void fallsBackToCoverLetterCreationDateWhenItHasNoItems() {
        User user = mock(User.class);
        CoverLetter coverLetter = mock(CoverLetter.class);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-12T18:10:00+09:00");
        when(userRepository.findById(56L)).thenReturn(Optional.of(user));
        when(resumeRepository.findByUserId(56L)).thenReturn(Optional.empty());
        when(coverLetterRepository.findByUserId(56L)).thenReturn(Optional.of(coverLetter));
        when(coverLetter.getId()).thenReturn(7L);
        when(coverLetter.getCreatedAt()).thenReturn(createdAt);
        when(coverLetterItemRepository.findLatestUpdatedAtByCoverLetterId(7L))
                .thenReturn(Optional.empty());

        var response = service.findMine();

        assertEquals(createdAt, response.coverLetterUpdatedAt());
    }

    @Test
    void rejectsMissingCurrentUser() {
        when(userRepository.findById(56L)).thenReturn(Optional.empty());

        MyPageException exception = assertThrows(MyPageException.class, service::findMine);

        assertEquals(ErrorCode.MY_PAGE_USER_NOT_FOUND, exception.getErrorCode());
    }
}
