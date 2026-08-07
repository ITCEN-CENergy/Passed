package com.cenergy.passed_backend.domain.user.repository;

import com.cenergy.passed_backend.domain.skill.entity.Skill;
import com.cenergy.passed_backend.domain.skill.entity.UserSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaUserSkillProviderTest {
    private UserSkillRepository userSkillRepository;
    private JpaUserSkillProvider provider;

    @BeforeEach
    void setUp() {
        userSkillRepository = mock(UserSkillRepository.class);
        provider = new JpaUserSkillProvider(userSkillRepository);
    }

    @Test
    void convertsUserSkillsLoadedFromRepository() {
        UserSkill userSkill = userSkill(12L, (short) 3, true);
        when(userSkillRepository.findAllByUserIdOrderBySkill_IdAsc(2L))
                .thenReturn(List.of(userSkill));

        var result = provider.findByUserId(2L);

        assertEquals(1, result.size());
        assertEquals(12L, result.getFirst().skillId());
        assertEquals((short) 3, result.getFirst().skillLevel());
        assertTrue(result.getFirst().important());
        verify(userSkillRepository).findAllByUserIdOrderBySkill_IdAsc(2L);
    }

    private UserSkill userSkill(Long skillId, short skillLevel, boolean important) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(skillId);

        UserSkill userSkill = mock(UserSkill.class);
        when(userSkill.getSkill()).thenReturn(skill);
        when(userSkill.getSkillLevel()).thenReturn(skillLevel);
        when(userSkill.isImportant()).thenReturn(important);
        return userSkill;
    }
}
