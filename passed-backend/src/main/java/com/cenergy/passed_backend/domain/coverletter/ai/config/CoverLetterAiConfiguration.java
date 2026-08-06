package com.cenergy.passed_backend.domain.coverletter.ai.config;

import com.cenergy.passed_backend.domain.coverletter.ai.client.CoverLetterAiClient;
import com.cenergy.passed_backend.domain.coverletter.ai.client.HttpCoverLetterAiClient;
import com.cenergy.passed_backend.domain.coverletter.ai.validation.CoverLetterAiResponseValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(CoverLetterAiProperties.class)
public class CoverLetterAiConfiguration {

    @Bean
    CoverLetterAiClient coverLetterAiClient(
            CoverLetterAiProperties properties,
            CoverLetterAiResponseValidator validator
    ) {
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
        return new HttpCoverLetterAiClient(restClient, validator);
    }
}
