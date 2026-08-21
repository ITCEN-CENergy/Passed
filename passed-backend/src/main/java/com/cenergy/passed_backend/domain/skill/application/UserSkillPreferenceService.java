package com.cenergy.passed_backend.domain.skill.application;

import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillListResponse;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillPreferenceItemRequest;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillPreferenceUpdateRequest;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillResponse;
import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;
import com.cenergy.passed_backend.domain.skill.entity.UserSkill;
import com.cenergy.passed_backend.domain.user.repository.UserSkillRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserSkillPreferenceService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final UserSkillRepository userSkillRepository;
    private final UserSkillAdvisoryLock advisoryLock;

    public UserSkillPreferenceService(
            CurrentUserIdProvider currentUserIdProvider,
            UserSkillRepository userSkillRepository,
            UserSkillAdvisoryLock advisoryLock
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.userSkillRepository = userSkillRepository;
        this.advisoryLock = advisoryLock;
    }

    @Transactional
    public UserSkillListResponse update(UserSkillPreferenceUpdateRequest request) {
        if (request == null || request.skills() == null) {
            throw invalid("skills are required");
        }

        Long userId = currentUserId();
        advisoryLock.lock(userId);
        List<UserSkill> currentSkills = userSkillRepository.findAllForUpdateByUserId(userId);
        validateMinimumSkillCount(currentSkills.size());

        Map<Long, UserSkillPreferenceItemRequest> requestedById = indexRequests(request.skills());
        validateSameSkillSet(currentSkills, requestedById.keySet());
        validateImportantCount(request.skills(), currentSkills.size());
        validateLevels(currentSkills, requestedById);

        currentSkills.forEach(userSkill -> {
            UserSkillPreferenceItemRequest preference = requestedById.get(userSkill.getId());
            userSkill.applyPreference(
                    preference.level().shortValue(),
                    preference.isImportantForMatching()
            );
        });

        List<UserSkillResponse> responses = currentSkills.stream()
                .map(UserSkillResponse::from)
                .toList();
        return new UserSkillListResponse(
                responses.size(),
                UserSkillPolicy.maxImportantCount(responses.size()),
                true,
                responses
        );
    }

    private Map<Long, UserSkillPreferenceItemRequest> indexRequests(
            List<UserSkillPreferenceItemRequest> requests
    ) {
        Map<Long, UserSkillPreferenceItemRequest> indexed = new HashMap<>();
        for (UserSkillPreferenceItemRequest request : requests) {
            if (request == null || request.userSkillId() == null
                    || request.level() == null || request.isImportantForMatching() == null) {
                throw invalid("Every skill preference field is required");
            }
            if (indexed.put(request.userSkillId(), request) != null) {
                throw invalid("Duplicate userSkillId is not allowed");
            }
        }
        return indexed;
    }

    private void validateMinimumSkillCount(int totalSkillCount) {
        if (totalSkillCount < UserSkillPolicy.MINIMUM_ANALYZED_SKILL_COUNT) {
            throw new UserSkillException(
                    ErrorCode.USER_SKILL_INSUFFICIENT,
                    "At least four analyzed skills are required"
            );
        }
    }

    private void validateSameSkillSet(List<UserSkill> currentSkills, Set<Long> requestedIds) {
        Set<Long> currentIds = new HashSet<>();
        currentSkills.forEach(skill -> currentIds.add(skill.getId()));
        if (!currentIds.equals(requestedIds)) {
            throw invalid("The request must contain all and only the current user's skills");
        }
    }

    private void validateImportantCount(
            List<UserSkillPreferenceItemRequest> requests,
            int totalSkillCount
    ) {
        long importantCount = requests.stream()
                .filter(UserSkillPreferenceItemRequest::isImportantForMatching)
                .count();
        int maximum = UserSkillPolicy.maxImportantCount(totalSkillCount);
        if (importantCount < 3 || importantCount > maximum) {
            throw invalid("Important skills must be between 3 and " + maximum);
        }
    }

    private void validateLevels(
            List<UserSkill> currentSkills,
            Map<Long, UserSkillPreferenceItemRequest> requestedById
    ) {
        for (UserSkill userSkill : currentSkills) {
            Integer level = requestedById.get(userSkill.getId()).level();
            if (level < 1 || level > 3) {
                throw invalid("Skill level must be between 1 and 3");
            }
            if ((userSkill.getSkill().getCategory() == SkillCategory.CERTIFICATION
                    || userSkill.getSkill().getCategory() == SkillCategory.BEHAVIORAL_TRAIT)
                    && level != 1) {
                throw invalid("Certification and behavioral trait levels must be 1");
            }
        }
    }

    private Long currentUserId() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw invalid("Current user is required");
        }
        return userId;
    }

    private UserSkillException invalid(String message) {
        return new UserSkillException(ErrorCode.USER_SKILL_INVALID_REQUEST, message);
    }
}
