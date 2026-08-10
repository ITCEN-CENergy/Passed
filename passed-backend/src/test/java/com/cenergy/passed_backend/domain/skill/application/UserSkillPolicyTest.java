package com.cenergy.passed_backend.domain.skill.application;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UserSkillPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1",
            "2, 2",
            "4, 3",
            "5, 3",
            "10, 3",
            "11, 4",
            "12, 4",
            "15, 5",
            "20, 6",
            "30, 9"
    })
    void calculatesMaximumImportantCount(int total, int expected) {
        assertThat(UserSkillPolicy.maxImportantCount(total)).isEqualTo(expected);
    }
}
