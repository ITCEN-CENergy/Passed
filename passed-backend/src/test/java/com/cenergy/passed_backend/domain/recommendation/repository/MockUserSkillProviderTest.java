package com.cenergy.passed_backend.domain.recommendation.repository;

import org.junit.jupiter.api.Test;
import com.cenergy.passed_backend.domain.user.repository.MockUserSkillProvider;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MockUserSkillProviderTest {
    private final MockUserSkillProvider provider = new MockUserSkillProvider();

    @Test
    void returnsFifteenUniqueSkillsAndFiveImportantSkillsForUserTwo() {
        var skills = provider.findByUserId(2L);

        assertEquals(15, skills.size());
        assertEquals(15, skills.stream().map(skill -> skill.skillId()).distinct().count());
        assertEquals(5, skills.stream().filter(skill -> skill.important()).count());
        assertEquals(1, skills.stream().filter(skill -> skill.skillId() == 16L).count());
    }

    @Test
    void returnsEmptySkillsForUnknownUser() {
        assertEquals(0, provider.findByUserId(999L).size());
    }
}
