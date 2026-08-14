package com.cenergy.passed_backend.domain.auth;

import com.cenergy.passed_backend.domain.auth.entity.CustomUserDetails;
import com.cenergy.passed_backend.domain.roadmap.application.SecurityCurrentUserIdProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityCurrentUserIdProviderTest {

    private final SecurityCurrentUserIdProvider provider = new SecurityCurrentUserIdProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsAuthenticatedPrincipalId() {
        CustomUserDetails principal =
                new CustomUserDetails(42L, "user@example.com", "encoded", "사용자", com.cenergy.passed_backend.domain.user.entity.UserRole.GENERAL_USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );

        assertThat(provider.getCurrentUserId()).isEqualTo(42L);
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThatThrownBy(provider::getCurrentUserId)
                .isInstanceOf(InsufficientAuthenticationException.class);
    }
}
