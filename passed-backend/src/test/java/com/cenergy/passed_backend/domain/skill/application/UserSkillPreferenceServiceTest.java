package com.cenergy.passed_backend.domain.skill.application;

import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillPreferenceItemRequest;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillPreferenceUpdateRequest;
import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;
import com.cenergy.passed_backend.domain.skill.entity.UserSkill;
import com.cenergy.passed_backend.domain.user.repository.UserSkillRepository;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSkillPreferenceServiceTest {
    private final CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
    private final UserSkillRepository userSkillRepository = mock(UserSkillRepository.class);
    private final UserSkillAdvisoryLock advisoryLock = mock(UserSkillAdvisoryLock.class);
    private final UserSkillPreferenceService service = new UserSkillPreferenceService(
            currentUserIdProvider,
            userSkillRepository,
            advisoryLock
    );

    @BeforeEach
    void setUp() {
        when(currentUserIdProvider.getCurrentUserId()).thenReturn(257L);
    }

    @Test
    void acceptsThreeImportantSkillsAndUpdatesTheWholeOwnedSet() {
        List<UserSkill> skills = fourSkills();
        when(userSkillRepository.findAllForUpdateByUserId(257L)).thenReturn(skills);

        var response = service.update(request(
                item(1L, 1, true),
                item(2L, 2, true),
                item(3L, 3, true),
                item(4L, 1, false)
        ));

        assertThat(response.totalSkillCount()).isEqualTo(4);
        assertThat(response.maxImportantCount()).isEqualTo(3);
        verify(skills.get(0)).applyPreference((short) 1, true);
        verify(skills.get(1)).applyPreference((short) 2, true);

        InOrder order = inOrder(advisoryLock, userSkillRepository);
        order.verify(advisoryLock).lock(257L);
        order.verify(userSkillRepository).findAllForUpdateByUserId(257L);
    }

    @Test
    void rejectsFewerThanThreeImportantSkills() {
        List<UserSkill> skills = fourSkills();
        when(userSkillRepository.findAllForUpdateByUserId(257L)).thenReturn(skills);

        assertThatThrownBy(() -> service.update(request(
                item(1L, 1, true),
                item(2L, 2, true),
                item(3L, 3, false),
                item(4L, 1, false)
        )))
                .isInstanceOfSatisfying(UserSkillException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_SKILL_INVALID_REQUEST));

        skills.forEach(skill -> verify(skill, never()).applyPreference(org.mockito.ArgumentMatchers.anyShort(), org.mockito.ArgumentMatchers.anyBoolean()));
    }

    @Test
    void rejectsARequestThatOmitsOrAddsAnotherUsersSkill() {
        List<UserSkill> skills = fourSkills();
        when(userSkillRepository.findAllForUpdateByUserId(257L)).thenReturn(skills);

        assertThatThrownBy(() -> service.update(request(
                item(1L, 1, false),
                item(2L, 2, false),
                item(3L, 3, false),
                item(999L, 1, false)
        )))
                .isInstanceOfSatisfying(UserSkillException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_SKILL_INVALID_REQUEST));

        skills.forEach(skill -> verify(skill, never()).applyPreference(org.mockito.ArgumentMatchers.anyShort(), org.mockito.ArgumentMatchers.anyBoolean()));
    }

    @Test
    void rejectsMoreThanThirtyPercentBeforeChangingAnyEntity() {
        List<UserSkill> skills = fourSkills();
        when(userSkillRepository.findAllForUpdateByUserId(257L)).thenReturn(skills);

        assertThatThrownBy(() -> service.update(request(
                item(1L, 1, true),
                item(2L, 2, true),
                item(3L, 3, true),
                item(4L, 1, true)
        )))
                .isInstanceOfSatisfying(UserSkillException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_SKILL_INVALID_REQUEST));

        skills.forEach(skill -> verify(skill, never()).applyPreference(org.mockito.ArgumentMatchers.anyShort(), org.mockito.ArgumentMatchers.anyBoolean()));
    }

    @Test
    void rejectsCertificationLevelTwoBeforeChangingAnyEntity() {
        List<UserSkill> skills = fourSkills();
        when(skills.get(3).getSkill().getCategory()).thenReturn(SkillCategory.CERTIFICATION);
        when(userSkillRepository.findAllForUpdateByUserId(257L)).thenReturn(skills);

        assertThatThrownBy(() -> service.update(request(
                item(1L, 1, false),
                item(2L, 2, false),
                item(3L, 3, false),
                item(4L, 2, false)
        )))
                .isInstanceOfSatisfying(UserSkillException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_SKILL_INVALID_REQUEST));

        skills.forEach(skill -> verify(skill, never()).applyPreference(org.mockito.ArgumentMatchers.anyShort(), org.mockito.ArgumentMatchers.anyBoolean()));
    }

    @Test
    void rejectsPreferenceConfirmationWhenOnlyThreeSkillsExist() {
        List<UserSkill> skills = fourSkills().subList(0, 3);
        when(userSkillRepository.findAllForUpdateByUserId(257L)).thenReturn(skills);

        assertThatThrownBy(() -> service.update(request(
                item(1L, 1, false),
                item(2L, 2, false),
                item(3L, 3, false)
        )))
                .isInstanceOfSatisfying(UserSkillException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_SKILL_INSUFFICIENT));
    }

    private List<UserSkill> fourSkills() {
        return List.of(
                userSkill(1L, 101L, SkillCategory.TECHNICAL_SKILL, (short) 1),
                userSkill(2L, 102L, SkillCategory.EXPERIENCE, (short) 2),
                userSkill(3L, 103L, SkillCategory.BEHAVIORAL_TRAIT, (short) 3),
                userSkill(4L, 104L, SkillCategory.CERTIFICATION, (short) 1)
        );
    }

    private UserSkill userSkill(Long id, Long skillId, SkillCategory category, short level) {
        UserSkill userSkill = mock(UserSkill.class);
        Skill skill = mock(Skill.class);
        when(userSkill.getId()).thenReturn(id);
        when(userSkill.getSkill()).thenReturn(skill);
        when(userSkill.getSkillLevel()).thenReturn(level);
        when(skill.getId()).thenReturn(skillId);
        when(skill.getName()).thenReturn("skill-" + skillId);
        when(skill.getCategory()).thenReturn(category);
        return userSkill;
    }

    private UserSkillPreferenceUpdateRequest request(UserSkillPreferenceItemRequest... items) {
        return new UserSkillPreferenceUpdateRequest(List.of(items));
    }

    private UserSkillPreferenceItemRequest item(Long id, int level, boolean important) {
        return new UserSkillPreferenceItemRequest(id, level, important);
    }
}
