package com.cenergy.passed_backend.domain.recommendation.ai.config;

import com.cenergy.passed_backend.domain.recommendation.ai.client.HttpRecommendationExplanationClient;
import com.cenergy.passed_backend.domain.recommendation.ai.client.HttpRecommendationSkillVerificationClient;
import com.cenergy.passed_backend.domain.recommendation.application.RecommendationExplanationClient;
import com.cenergy.passed_backend.domain.recommendation.application.RecommendationSkillVerificationClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RecommendationAiProperties.class)
public class RecommendationAiConfiguration {

    @Bean
    RecommendationExplanationClient recommendationExplanationClient(
            RecommendationAiProperties properties
    ) {
        return new HttpRecommendationExplanationClient(restClient(properties));
    }

    @Bean
    RecommendationSkillVerificationClient recommendationSkillVerificationClient(
            RecommendationAiProperties properties
    ) {
        return new HttpRecommendationSkillVerificationClient(restClient(properties));
    }

    private RestClient restClient(RecommendationAiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));

        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
