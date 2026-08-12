package com.cenergy.passed_backend.domain.skill.application;

import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillEvidenceListResponse;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillEvidenceResponse;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillListResponse;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillResponse;
import com.cenergy.passed_backend.domain.skill.entity.UserSkillEvidence;
import com.cenergy.passed_backend.domain.skill.repository.UserSkillEvidenceRepository;
import com.cenergy.passed_backend.domain.user.repository.UserSkillRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserSkillQueryService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final UserSkillRepository userSkillRepository;
    private final UserSkillEvidenceRepository evidenceRepository;

    public UserSkillQueryService(
            CurrentUserIdProvider currentUserIdProvider,
            UserSkillRepository userSkillRepository,
            UserSkillEvidenceRepository evidenceRepository
    ) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.userSkillRepository = userSkillRepository;
        this.evidenceRepository = evidenceRepository;
    }

    public UserSkillListResponse findAll() {
        List<UserSkillResponse> skills = userSkillRepository
                .findAllByUserIdOrderBySkill_IdAsc(currentUserId()).stream()
                .map(UserSkillResponse::from)
                .toList();
        int total = skills.size();
        return new UserSkillListResponse(
                total,
                UserSkillPolicy.maxImportantCount(total),
                total >= UserSkillPolicy.MINIMUM_ANALYZED_SKILL_COUNT,
                skills
        );
    }

    public UserSkillEvidenceListResponse findEvidences(Long userSkillId) {
        if (userSkillId == null || userSkillId <= 0) {
            throw new UserSkillException(
                    ErrorCode.USER_SKILL_INVALID_REQUEST,
                    "userSkillId must be positive"
            );
        }
        List<UserSkillEvidence> evidences = evidenceRepository
                .findAllOwnedByUserSkillId(userSkillId, currentUserId());
        if (evidences.isEmpty()) {
            throw new UserSkillException(
                    ErrorCode.USER_SKILL_NOT_FOUND,
                    "User skill evidence not found"
            );
        }
        return new UserSkillEvidenceListResponse(
                userSkillId,
                evidences.stream().map(UserSkillEvidenceResponse::from).toList()
        );
    }

    private Long currentUserId() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new UserSkillException(
                    ErrorCode.USER_SKILL_INVALID_REQUEST,
                    "Current user is required"
            );
        }
        return userId;
    }
}
