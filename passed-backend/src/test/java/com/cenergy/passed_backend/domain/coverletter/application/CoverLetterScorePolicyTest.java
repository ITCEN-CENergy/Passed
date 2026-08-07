package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.ai.client.CoverLetterAiException;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterScore;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoverLetterScorePolicyTest {
    private final CoverLetterScorePolicy policy = new CoverLetterScorePolicy();

    @ParameterizedTest
    @CsvSource({
            "0, DEFICIENT",
            "59, DEFICIENT",
            "60, INSUFFICIENT",
            "79, INSUFFICIENT",
            "80, SUFFICIENT",
            "100, SUFFICIENT"
    })
    void mapsScoreBoundaries(int score, CoverLetterScore expected) {
        assertThat(policy.from(score)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void rejectsOutOfRangeScore(int score) {
        assertThatThrownBy(() -> policy.from(score))
                .isInstanceOf(CoverLetterAiException.class);
    }
}
