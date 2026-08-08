package com.cenergy.passed_backend.domain.coverletter.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoverLetterScoreConverterTest {
    private final CoverLetterScoreConverter converter = new CoverLetterScoreConverter();

    @Test
    void convertsScoreToKoreanDatabaseValue() {
        assertThat(converter.convertToDatabaseColumn(CoverLetterScore.SUFFICIENT)).isEqualTo("충분");
        assertThat(converter.convertToDatabaseColumn(CoverLetterScore.INSUFFICIENT)).isEqualTo("미흡");
        assertThat(converter.convertToDatabaseColumn(CoverLetterScore.DEFICIENT)).isEqualTo("부족");
    }

    @Test
    void convertsKoreanDatabaseValueToScore() {
        assertThat(converter.convertToEntityAttribute("충분")).isEqualTo(CoverLetterScore.SUFFICIENT);
        assertThat(converter.convertToEntityAttribute("미흡")).isEqualTo(CoverLetterScore.INSUFFICIENT);
        assertThat(converter.convertToEntityAttribute("부족")).isEqualTo(CoverLetterScore.DEFICIENT);
    }

    @Test
    void preservesNullAndRejectsUnknownValue() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> converter.convertToEntityAttribute("보통"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
