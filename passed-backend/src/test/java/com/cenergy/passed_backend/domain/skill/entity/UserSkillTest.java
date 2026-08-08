package com.cenergy.passed_backend.domain.skill.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class UserSkillTest {

    @Test
    void keepsLevelConfidenceWhenLevelDoesNotChange() {
        UserSkill userSkill = userSkill((short) 2, "0.840", "0.950");

        userSkill.applyPreference((short) 2, true);

        assertThat(userSkill.getSkillLevel()).isEqualTo((short) 2);
        assertThat(userSkill.getLevelConfidence()).isEqualByComparingTo("0.840");
        assertThat(userSkill.getMappingConfidence()).isEqualByComparingTo("0.950");
        assertThat(userSkill.isImportant()).isTrue();
    }

    @Test
    void clearsOnlyLevelConfidenceWhenUserChangesLevel() {
        UserSkill userSkill = userSkill((short) 2, "0.840", "0.950");

        userSkill.applyPreference((short) 3, false);

        assertThat(userSkill.getSkillLevel()).isEqualTo((short) 3);
        assertThat(userSkill.getLevelConfidence()).isNull();
        assertThat(userSkill.getMappingConfidence()).isEqualByComparingTo("0.950");
        assertThat(userSkill.isImportant()).isFalse();
    }

    private UserSkill userSkill(short level, String levelConfidence, String mappingConfidence) {
        UserSkill userSkill = new UserSkill();
        ReflectionTestUtils.setField(userSkill, "skillLevel", level);
        ReflectionTestUtils.setField(userSkill, "levelConfidence", new BigDecimal(levelConfidence));
        ReflectionTestUtils.setField(userSkill, "mappingConfidence", new BigDecimal(mappingConfidence));
        return userSkill;
    }
}
