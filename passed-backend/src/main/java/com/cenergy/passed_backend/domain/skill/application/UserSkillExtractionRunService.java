package com.cenergy.passed_backend.domain.skill.application;

import com.cenergy.passed_backend.domain.skill.dto.UserSkillExtractionRunResponse;
import com.cenergy.passed_backend.domain.skill.entity.UserSkillExtractionRun;
import com.cenergy.passed_backend.domain.skill.entity.UserSkillExtractionRunStatus;
import com.cenergy.passed_backend.domain.skill.repository.UserSkillExtractionRunRepository;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import org.springframework.stereotype.Service;

@Service
public class UserSkillExtractionRunService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final UserRepository userRepository;
    private final UserSkillExtractionRunRepository runRepository;
    private final UserSkillExtractionAsyncProcessor processor;

    public UserSkillExtractionRunService(
            CurrentUserIdProvider currentUserIdProvider,
            UserRepository userRepository,
            UserSkillExtractionRunRepository runRepository,
            UserSkillExtractionAsyncProcessor processor
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.userRepository = userRepository;
        this.runRepository = runRepository;
        this.processor = processor;
    }

    public UserSkillExtractionRunResponse start() {
        Long userId = currentUserId();
        var processing = runRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(
                userId, UserSkillExtractionRunStatus.PROCESSING
        );
        if (processing.isPresent()) return UserSkillExtractionRunResponse.from(processing.get());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> invalid("Current user not found"));
        UserSkillExtractionRun run = runRepository.saveAndFlush(UserSkillExtractionRun.start(user));
        processor.process(run.getId(), userId);
        return UserSkillExtractionRunResponse.from(run);
    }

    public UserSkillExtractionRunResponse findMine(Long extractionId) {
        UserSkillExtractionRun run = runRepository.findByIdAndUserId(extractionId, currentUserId())
                .orElseThrow(() -> invalid("Skill extraction run not found"));
        return UserSkillExtractionRunResponse.from(run);
    }

    private Long currentUserId() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) throw invalid("Current user is required");
        return userId;
    }

    private UserSkillException invalid(String message) {
        return new UserSkillException(ErrorCode.USER_SKILL_INVALID_REQUEST, message);
    }
}
