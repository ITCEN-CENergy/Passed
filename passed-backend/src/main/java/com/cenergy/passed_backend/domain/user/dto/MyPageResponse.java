package com.cenergy.passed_backend.domain.user.dto;

import java.time.OffsetDateTime;

/** 마이페이지 상단에 필요한 현재 사용자와 문서 상태를 한 번에 반환한다. */
public record MyPageResponse(
        String name,
        String email,
        String profileImageUrl,
        OffsetDateTime resumeUpdatedAt,
        OffsetDateTime coverLetterUpdatedAt
) {
}
