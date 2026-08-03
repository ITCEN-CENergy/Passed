package com.cenergy.passed_backend.domain.roadmap.ai.config;

import com.cenergy.passed_backend.domain.roadmap.ai.client.HttpRoadmapAiClient;
import com.cenergy.passed_backend.domain.roadmap.ai.client.RoadmapAiClient;
import com.cenergy.passed_backend.domain.roadmap.ai.validation.RoadmapAiResponseValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RoadmapAiProperties.class)
public class RoadmapAiConfiguration {

    @Bean
    RoadmapAiClient roadmapAiClient(
            RestClient.Builder builder,
            RoadmapAiProperties properties,
            RoadmapAiResponseValidator validator
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));

        RestClient restClient = builder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        return new HttpRoadmapAiClient(restClient, validator);
    }
}
