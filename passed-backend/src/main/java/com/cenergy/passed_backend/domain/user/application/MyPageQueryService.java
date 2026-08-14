package com.cenergy.passed_backend.domain.user.application;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetter;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterItemRepository;
import com.cenergy.passed_backend.domain.coverletter.repository.CoverLetterRepository;
import com.cenergy.passed_backend.domain.resume.entity.Resume;
import com.cenergy.passed_backend.domain.resume.repository.PersonalInfoRepository;
import com.cenergy.passed_backend.domain.resume.repository.ResumeRepository;
import com.cenergy.passed_backend.domain.user.dto.MyPageResponse;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class MyPageQueryService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final PersonalInfoRepository personalInfoRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final CoverLetterItemRepository coverLetterItemRepository;
    private final RecommendationRefreshStatusService refreshStatusService;

    public MyPageQueryService(
            CurrentUserIdProvider currentUserIdProvider,
            UserRepository userRepository,
            ResumeRepository resumeRepository,
            PersonalInfoRepository personalInfoRepository,
            CoverLetterRepository coverLetterRepository,
            CoverLetterItemRepository coverLetterItemRepository,
            RecommendationRefreshStatusService refreshStatusService
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.userRepository = userRepository;
        this.resumeRepository = resumeRepository;
        this.personalInfoRepository = personalInfoRepository;
        this.coverLetterRepository = coverLetterRepository;
        this.coverLetterItemRepository = coverLetterItemRepository;
        this.refreshStatusService = refreshStatusService;
    }

    @Transactional(readOnly = true)
    public MyPageResponse findMine() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new MyPageException(
                        ErrorCode.MY_PAGE_USER_NOT_FOUND,
                        "Current user not found"
                ));

        Resume resume = resumeRepository.findByUserId(userId).orElse(null);
        String profileImageUrl = resume == null ? null
                : personalInfoRepository.findByResumeId(resume.getId())
                        .map(personalInfo -> personalInfo.getPhotoUrl())
                        .orElse(null);

        OffsetDateTime resumeUpdatedAt = resume == null ? null : resume.getUpdatedAt();

        CoverLetter coverLetter = coverLetterRepository.findByUserId(userId).orElse(null);
        OffsetDateTime coverLetterUpdatedAt = coverLetter == null ? null
                : coverLetterItemRepository.findLatestUpdatedAtByCoverLetterId(coverLetter.getId())
                        .orElse(coverLetter.getCreatedAt());

        return new MyPageResponse(
                user.getName(),
                user.getEmail(),
                profileImageUrl,
                resumeUpdatedAt,
                coverLetterUpdatedAt,
                refreshStatusService.isRefreshRequired(userId, resumeUpdatedAt, coverLetterUpdatedAt)
        );
    }
}
