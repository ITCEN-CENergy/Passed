package com.cenergy.passed_backend.domain.roadmap.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

/** Temporary development identity until authentication is connected. */
@Component
@Profile("fixed-dev-user")
public class DevCurrentUserIdProvider implements CurrentUserIdProvider {
    private final Long devUserId;

    public DevCurrentUserIdProvider(
            @Value("${app.dev-current-user-id:257}") Long devUserId
    ) {
        if (devUserId == null || devUserId <= 0) {
            throw new IllegalArgumentException("app.dev-current-user-id must be positive");
        }
        this.devUserId = devUserId;
    }

    @Override
    public Long getCurrentUserId() {
        return devUserId;
    }
}
