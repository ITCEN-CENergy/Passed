package com.cenergy.passed_backend.domain.recommendation.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "external.recommendation-ai")
public record RecommendationAiProperties(
        URI baseUrl,
        int connectTimeoutMillis,
        int readTimeoutMillis
) {
    public RecommendationAiProperties {
        if (baseUrl == null || !baseUrl.isAbsolute()) {
            throw new IllegalArgumentException("external.recommendation-ai.base-url must be absolute");
        }
        if (connectTimeoutMillis < 1 || readTimeoutMillis < 1) {
            throw new IllegalArgumentException("recommendation AI timeouts must be positive");
        }
    }
}
