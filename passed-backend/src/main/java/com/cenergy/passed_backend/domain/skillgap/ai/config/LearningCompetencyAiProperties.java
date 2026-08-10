package com.cenergy.passed_backend.domain.skillgap.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "external.learning-competency-ai")
public record LearningCompetencyAiProperties(
        URI baseUrl,
        int connectTimeoutMillis,
        int readTimeoutMillis
) {
    public LearningCompetencyAiProperties {
        if (baseUrl == null || !baseUrl.isAbsolute()) {
            throw new IllegalArgumentException("external.learning-competency-ai.base-url must be absolute");
        }
        if (connectTimeoutMillis < 1 || readTimeoutMillis < 1) {
            throw new IllegalArgumentException("learning competency AI timeouts must be positive");
        }
    }
}
