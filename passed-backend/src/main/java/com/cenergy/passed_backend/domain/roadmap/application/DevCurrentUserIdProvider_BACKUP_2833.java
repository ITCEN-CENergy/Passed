package com.cenergy.passed_backend.domain.roadmap.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

/**
 * Temporary development identity until authentication is connected.
 */
@Component
<<<<<<< HEAD
@Profile("fixed-dev-user")
=======
@Profile("!prod")
>>>>>>> 10a3e5686c991b7b7dc985b3a8f7298100d16b9c
public class DevCurrentUserIdProvider implements CurrentUserIdProvider {
    private final Long devUserId;

    public DevCurrentUserIdProvider(
            @Value("${app.dev-current-user-id:258}") Long devUserId
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
