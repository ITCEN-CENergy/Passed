package com.cenergy.passed_backend.domain.skill.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "external.user-skill-ai")
public record UserSkillAiProperties(
        URI baseUrl,
        int connectTimeoutMillis,
        int readTimeoutMillis
) {
    public UserSkillAiProperties {
        if (baseUrl == null || !baseUrl.isAbsolute()) {
            throw new IllegalArgumentException("external.user-skill-ai.base-url must be absolute");
        }
        if (connectTimeoutMillis < 1 || readTimeoutMillis < 1) {
            throw new IllegalArgumentException("user skill AI timeouts must be positive");
        }
    }
}
