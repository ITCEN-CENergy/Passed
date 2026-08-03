package com.cenergy.passed_backend.domain.roadmap.application;

import org.springframework.stereotype.Component;

/** Temporary development identity until authentication is connected. */
@Component
public class DevCurrentUserIdProvider implements CurrentUserIdProvider {
    private static final Long DEV_USER_ID = 1L;

    @Override
    public Long getCurrentUserId() {
        return DEV_USER_ID;
    }
}
