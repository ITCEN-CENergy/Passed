package com.cenergy.passed_backend.global.config;

import com.cenergy.passed_backend.domain.auth.entity.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Service
public class JwtProvider {

    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    @Value("${application.security.jwt.secret}")
    private String secretKey;

    @Value("${application.security.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${application.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @PostConstruct
    void validateConfiguration() {
        if (secretKey == null || secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        }
        if (accessTokenExpiration <= 0 || refreshTokenExpiration <= accessTokenExpiration) {
            throw new IllegalStateException(
                    "JWT token expiration must be positive and refresh expiration must exceed access expiration"
            );
        }
    }

    public String generateAccessToken(CustomUserDetails user) {
        return buildToken(user, ACCESS, accessTokenExpiration, true);
    }

    public String generateRefreshToken(CustomUserDetails user) {
        return buildToken(user, REFRESH, refreshTokenExpiration, false);
    }

    public Long extractUserId(String token) {
        return Long.valueOf(extractClaims(token).getSubject());
    }

    public String extractUsername(String token) {
        return extractClaims(token).get("email", String.class);
    }

    public boolean isAccessToken(String token) {
        return ACCESS.equals(extractType(token));
    }

    public boolean isRefreshToken(String token) {
        return REFRESH.equals(extractType(token));
    }

    public boolean isValidAccessToken(String token, CustomUserDetails user) {
        return isValidFor(token, user, ACCESS);
    }

    public boolean isValidRefreshToken(String token, CustomUserDetails user) {
        return isValidFor(token, user, REFRESH);
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public Duration accessTokenDuration() {
        return Duration.ofMillis(accessTokenExpiration);
    }

    public Duration refreshTokenDuration() {
        return Duration.ofMillis(refreshTokenExpiration);
    }

    private String buildToken(
            CustomUserDetails user,
            String type,
            long expirationMillis,
            boolean includeEmail
    ) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .id(UUID.randomUUID().toString())
                .claim("type", type)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMillis));
        if (includeEmail) {
            builder.claim("email", user.getEmail());
        }
        return builder.signWith(signingKey()).compact();
    }

    private boolean isValidFor(String token, CustomUserDetails user, String expectedType) {
        try {
            Claims claims = extractClaims(token);
            return expectedType.equals(claims.get("type", String.class))
                    && Objects.equals(String.valueOf(user.getUserId()), claims.getSubject())
                    && (REFRESH.equals(expectedType)
                    || Objects.equals(user.getEmail(), claims.get("email", String.class)));
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private String extractType(String token) {
        return extractClaims(token).get("type", String.class);
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}
