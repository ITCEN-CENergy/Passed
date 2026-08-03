package com.cenergy.passed_backend.domain.roadmap.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "external.roadmap-ai")
public record RoadmapAiProperties(
        URI baseUrl,
        int connectTimeoutMillis,
        int readTimeoutMillis
) {
    public RoadmapAiProperties {
        if (baseUrl == null || !baseUrl.isAbsolute()) {
            throw new IllegalArgumentException("external.roadmap-ai.base-url must be absolute");
        }
        if (connectTimeoutMillis < 1 || readTimeoutMillis < 1) {
            throw new IllegalArgumentException("roadmap AI timeouts must be positive");
        }
    }
}
