package com.cenergy.passed_backend.domain.roadmap.application;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;

/**
 * Resolves the production user from the authenticated servlet principal.
 *
 * <p>The application intentionally fails the request when authentication has
 * not established a numeric user principal. It must never fall back to the
 * fixed development user in production.</p>
 */
@Component
@Profile("prod")
public class ProdCurrentUserIdProvider implements CurrentUserIdProvider {

    @Override
    public Long getCurrentUserId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new IllegalStateException("Authenticated request context is required");
        }

        HttpServletRequest request = attributes.getRequest();
        Principal principal = request.getUserPrincipal();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalStateException("Authenticated user principal is required");
        }

        try {
            long userId = Long.parseLong(principal.getName());
            if (userId <= 0) {
                throw new IllegalStateException("Authenticated user ID must be positive");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Authenticated principal name must be a numeric user ID", exception);
        }
    }
}
