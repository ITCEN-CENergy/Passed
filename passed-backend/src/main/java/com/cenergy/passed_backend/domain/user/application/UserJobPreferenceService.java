package com.cenergy.passed_backend.domain.user.application;

import com.cenergy.passed_backend.domain.jobposting.entity.Industry;
import com.cenergy.passed_backend.domain.jobposting.entity.JobRole;
import com.cenergy.passed_backend.domain.jobposting.repository.IndustryRepository;
import com.cenergy.passed_backend.domain.jobposting.repository.JobRoleRepository;
import com.cenergy.passed_backend.domain.user.dto.*;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.domain.roadmap.application.CurrentUserIdProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class UserJobPreferenceService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final UserRepository userRepository;
    private final IndustryRepository industryRepository;
    private final JobRoleRepository jobRoleRepository;

    public UserJobPreferenceService(
            CurrentUserIdProvider currentUserIdProvider,
            UserRepository userRepository,
            IndustryRepository industryRepository,
            JobRoleRepository jobRoleRepository
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.userRepository = userRepository;
        this.industryRepository = industryRepository;
        this.jobRoleRepository = jobRoleRepository;
    }

    public IndustryListResponse findIndustries() {
        return new IndustryListResponse(
                industryRepository.findAllByOrderByIdAsc().stream()
                        .filter(industry -> containsHangul(industry.getIndustryName()))
                        .map(IndustryResponse::from)
                        .toList()
        );
    }

    public UserJobPreferenceResponse findCurrent() {
        User user = userRepository.findById(currentUserId())
                .orElseThrow(() -> new UserPreferenceException(
                        ErrorCode.USER_PREFERENCE_USER_NOT_FOUND,
                        "Current user not found"
                ));
        if (user.getDesiredIndustry() == null
                || !containsHangul(user.getDesiredIndustry().getIndustryName())
                || user.getDesiredJobRoles().isEmpty()) {
            return null;
        }
        return toResponse(
                user,
                user.getDesiredIndustry(),
                user.getDesiredJobRoles().stream()
                        .sorted((left, right) -> left.getId().compareTo(right.getId()))
                        .toList()
        );
    }

    public JobRoleListResponse findJobRoles(Long industryId) {
        Industry industry = findIndustry(industryId);
        List<JobRoleResponse> jobRoles = jobRoleRepository
                .findAllByIndustryIdOrderByIdAsc(industryId).stream()
                .map(JobRoleResponse::from)
                .toList();
        return new JobRoleListResponse(IndustryResponse.from(industry), jobRoles);
    }

    @Transactional
    public UserJobPreferenceResponse update(UserJobPreferenceUpdateRequest request) {
        validateRequest(request);
        Long userId = currentUserId();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserPreferenceException(
                        ErrorCode.USER_PREFERENCE_USER_NOT_FOUND,
                        "Current user not found"
                ));
        Industry industry = findIndustry(request.industryId());
        List<JobRole> jobRoles = jobRoleRepository.findAllByIdIn(request.jobRoleIds()).stream()
                .sorted((left, right) -> left.getId().compareTo(right.getId()))
                .toList();

        validateJobRoles(request.jobRoleIds(), industry, jobRoles);
        user.updateJobPreferences(industry, jobRoles);
        User saved = userRepository.saveAndFlush(user);

        return toResponse(saved, industry, jobRoles);
    }

    private UserJobPreferenceResponse toResponse(
            User user,
            Industry industry,
            List<JobRole> jobRoles
    ) {
        return new UserJobPreferenceResponse(
                user.getId(),
                IndustryResponse.from(industry),
                jobRoles.stream().map(JobRoleResponse::from).toList(),
                user.getUpdatedAt()
        );
    }

    private boolean containsHangul(String value) {
        return value != null && value.codePoints()
                .anyMatch(codePoint -> codePoint >= '가' && codePoint <= '힣');
    }

    private Industry findIndustry(Long industryId) {
        if (industryId == null || industryId <= 0) {
            throw invalid("industryId must be positive");
        }
        return industryRepository.findById(industryId)
                .orElseThrow(() -> new UserPreferenceException(
                        ErrorCode.USER_PREFERENCE_INDUSTRY_NOT_FOUND,
                        "Industry not found"
                ));
    }

    private void validateRequest(UserJobPreferenceUpdateRequest request) {
        if (request == null || request.industryId() == null || request.industryId() <= 0
                || request.jobRoleIds() == null || request.jobRoleIds().isEmpty()
                || request.jobRoleIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw invalid("industryId and jobRoleIds are required");
        }
        if (new HashSet<>(request.jobRoleIds()).size() != request.jobRoleIds().size()) {
            throw invalid("jobRoleIds must not contain duplicates");
        }
    }

    private void validateJobRoles(
            List<Long> requestedIds,
            Industry industry,
            List<JobRole> jobRoles
    ) {
        Set<Long> loadedIds = jobRoles.stream().map(JobRole::getId).collect(java.util.stream.Collectors.toSet());
        if (!loadedIds.containsAll(requestedIds)) {
            throw new UserPreferenceException(
                    ErrorCode.USER_PREFERENCE_JOB_ROLE_NOT_FOUND,
                    "One or more job roles were not found"
            );
        }
        if (jobRoles.stream().anyMatch(role -> !industry.getId().equals(role.getIndustry().getId()))) {
            throw new UserPreferenceException(
                    ErrorCode.USER_PREFERENCE_JOB_ROLE_INDUSTRY_MISMATCH,
                    "Every job role must belong to the selected industry"
            );
        }
    }

    private Long currentUserId() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw invalid("Current user is required");
        }
        return userId;
    }

    private UserPreferenceException invalid(String message) {
        return new UserPreferenceException(ErrorCode.USER_PREFERENCE_INVALID_REQUEST, message);
    }
}
