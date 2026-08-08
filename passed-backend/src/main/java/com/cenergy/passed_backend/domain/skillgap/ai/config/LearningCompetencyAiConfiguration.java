package com.cenergy.passed_backend.domain.skillgap.ai.config;

import com.cenergy.passed_backend.domain.skillgap.ai.client.HttpLearningCompetencyAiClient;
import com.cenergy.passed_backend.domain.skillgap.ai.client.LearningCompetencyAiClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(LearningCompetencyAiProperties.class)
public class LearningCompetencyAiConfiguration {

    @Bean
    LearningCompetencyAiClient learningCompetencyAiClient(LearningCompetencyAiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        return new HttpLearningCompetencyAiClient(restClient);
    }
}
