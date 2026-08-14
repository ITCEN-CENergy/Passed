package com.cenergy.passed_backend.domain.auth;

import com.cenergy.passed_backend.domain.auth.entity.CustomUserDetails;
import com.cenergy.passed_backend.global.config.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private CustomUserDetails principal;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(
                jwtProvider,
                "secretKey",
                "test-secret-key-with-at-least-thirty-two-bytes"
        );
        ReflectionTestUtils.setField(jwtProvider, "accessTokenExpiration", 1_800_000L);
        ReflectionTestUtils.setField(jwtProvider, "refreshTokenExpiration", 604_800_000L);
        principal = new CustomUserDetails(42L, "user@example.com", "encoded", "사용자", com.cenergy.passed_backend.domain.user.entity.UserRole.GENERAL_USER);
    }

    @Test
    void accessTokenContainsUserIdentityAndCannotBeUsedAsRefreshToken() {
        String token = jwtProvider.generateAccessToken(principal);

        assertThat(jwtProvider.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtProvider.extractUsername(token)).isEqualTo("user@example.com");
        assertThat(jwtProvider.isAccessToken(token)).isTrue();
        assertThat(jwtProvider.isRefreshToken(token)).isFalse();
        assertThat(jwtProvider.isValidAccessToken(token, principal)).isTrue();
    }

    @Test
    void refreshTokenIsBoundToItsUser() {
        String token = jwtProvider.generateRefreshToken(principal);
        CustomUserDetails another =
                new CustomUserDetails(43L, "other@example.com", "encoded", "다른 사용자", com.cenergy.passed_backend.domain.user.entity.UserRole.GENERAL_USER);

        assertThat(jwtProvider.isRefreshToken(token)).isTrue();
        assertThat(jwtProvider.isValidRefreshToken(token, principal)).isTrue();
        assertThat(jwtProvider.isValidRefreshToken(token, another)).isFalse();
    }
}
