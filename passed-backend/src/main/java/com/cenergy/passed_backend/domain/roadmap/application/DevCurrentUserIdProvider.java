package com.cenergy.passed_backend.domain.roadmap.application;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

/** Temporary development identity until authentication is connected. */
@Component
@Profile("fixed-dev-user")
public class DevCurrentUserIdProvider implements CurrentUserIdProvider {
    private static final Long DEV_USER_ID = 257L;

    @Override
    public Long getCurrentUserId() {
        return DEV_USER_ID;
    }
}
