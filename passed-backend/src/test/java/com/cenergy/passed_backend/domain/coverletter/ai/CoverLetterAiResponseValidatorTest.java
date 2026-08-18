package com.cenergy.passed_backend.domain.coverletter.ai;

import com.cenergy.passed_backend.domain.coverletter.ai.exception.CoverLetterAiException;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiResponse;
import com.cenergy.passed_backend.domain.coverletter.ai.validation.CoverLetterAiResponseValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoverLetterAiResponseValidatorTest {
    private final CoverLetterAiResponseValidator validator = new CoverLetterAiResponseValidator();

    @Test
    void validatesCompleteResponse() {
        var result = validator.validate(response(84));

        assertThat(result.qaAlignmentScore()).isEqualTo(84);
        assertThat(result.shortcomings()).isEqualTo("미흡한 부분");
        assertThat(result.recommendedRevisionDirection()).isEqualTo("추천 수정 방향");
    }

    @Test
    void rejectsOutOfRangeScore() {
        assertThatThrownBy(() -> validator.validate(response(101)))
                .isInstanceOf(CoverLetterAiException.class);
    }

    @Test
    void rejectsBlankRequiredText() {
        CoverLetterAiResponse response = new CoverLetterAiResponse(
                80,
                " ",
                "추천 수정 방향"
        );

        assertThatThrownBy(() -> validator.validate(response))
                .isInstanceOf(CoverLetterAiException.class);
    }

    private CoverLetterAiResponse response(int score) {
        return new CoverLetterAiResponse(
                score,
                "미흡한 부분",
                "추천 수정 방향"
        );
    }
}
