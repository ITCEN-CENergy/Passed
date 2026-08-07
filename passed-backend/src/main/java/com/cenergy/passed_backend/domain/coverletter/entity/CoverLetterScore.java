package com.cenergy.passed_backend.domain.coverletter.entity;

import lombok.Getter;

import java.util.Arrays;

/**
 * The qualitative labels persisted by the company-cover-letter feedback schema.
 * Unicode escapes keep the Korean database contract independent of source-file encoding.
 */
@Getter
public enum CoverLetterScore {
    /** The submitted answer sufficiently satisfies the feedback criteria. */
    SUFFICIENT("충분"),
    /** The submitted answer needs meaningful improvement. */
    INSUFFICIENT("미흡"),
    /** The submitted answer lacks the required evidence or quality. */
    DEFICIENT("부족");

    /** The exact label stored in the VARCHAR score columns.
     * -- GETTER --
     * Returns the exact value accepted by the database CHECK constraints.
     */
    private final String databaseValue;

    /** Creates one score label with its persisted Korean text. */
    CoverLetterScore(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    /** Converts a persisted Korean score label back into the application enum. */
    public static CoverLetterScore fromDatabaseValue(String value) {
        return Arrays.stream(values())
                .filter(score -> score.databaseValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown cover letter score: " + value));
    }
}
