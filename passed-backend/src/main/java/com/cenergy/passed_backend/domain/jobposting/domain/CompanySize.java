package com.cenergy.passed_backend.jobposting.entity;

public enum CompanySize {
    LARGE_ENTERPRISE("대기업"),
    PUBLIC_INSTITUTION("공공기관"),
    STARTUP("스타트업"),
    MID_SIZED_ENTERPRISE("중견기업"),
    SMALL_AND_MEDIUM_ENTERPRISE("중소기업");

    private final String label;

    CompanySize(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
