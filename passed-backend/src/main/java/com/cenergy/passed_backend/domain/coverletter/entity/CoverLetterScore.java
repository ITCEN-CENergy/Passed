package com.cenergy.passed_backend.domain.coverletter.entity;

import java.util.Arrays;

public enum CoverLetterScore {
    SUFFICIENT("충분"),
    INSUFFICIENT("미흡"),
    DEFICIENT("부족");

    private final String databaseValue;

    CoverLetterScore(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String getDatabaseValue() {
        return databaseValue;
    }

    public static CoverLetterScore fromDatabaseValue(String value) {
        return Arrays.stream(values())
                .filter(score -> score.databaseValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown cover letter score: " + value));
    }
}
