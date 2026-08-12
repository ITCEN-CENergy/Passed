package com.cenergy.passed_backend.domain.skill.application;

import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import com.cenergy.passed_backend.domain.skill.repository.UserSkillEvidenceRepository;
import com.cenergy.passed_backend.domain.user.repository.UserSkillRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserSkillQueryServiceTest {

    @Test
    void hidesAnUnownedOrUnknownUserSkillAsNotFound() {
        CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
        UserSkillRepository userSkillRepository = mock(UserSkillRepository.class);
        UserSkillEvidenceRepository evidenceRepository = mock(UserSkillEvidenceRepository.class);
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(257L);
        when(evidenceRepository.findAllOwnedByUserSkillId(99L, 257L)).thenReturn(List.of());
        UserSkillQueryService service = new UserSkillQueryService(
                currentUserIdProvider,
                userSkillRepository,
                evidenceRepository
        );

        assertThatThrownBy(() -> service.findEvidences(99L))
                .isInstanceOfSatisfying(UserSkillException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_SKILL_NOT_FOUND));
    }
}
