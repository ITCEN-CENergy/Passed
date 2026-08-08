package com.cenergy.passed_backend.domain.coverletter.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "external.cover-letter-ai")
public record CoverLetterAiProperties(
        URI baseUrl,
        int connectTimeoutMillis,
        int readTimeoutMillis
) {
    public CoverLetterAiProperties {
        if (baseUrl == null || !baseUrl.isAbsolute()) {
            throw new IllegalArgumentException("external.cover-letter-ai.base-url must be absolute");
        }
        if (connectTimeoutMillis < 1 || readTimeoutMillis < 1) {
            throw new IllegalArgumentException("cover letter AI timeouts must be positive");
        }
    }
}
