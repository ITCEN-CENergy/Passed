package com.cenergy.passed_backend.domain.recommendation.ai.client;

import com.cenergy.passed_backend.domain.recommendation.ai.dto.RecommendationExplanationAiRequest;
import com.cenergy.passed_backend.domain.recommendation.ai.dto.RecommendationExplanationAiResponse;
import com.cenergy.passed_backend.domain.recommendation.application.RecommendationExplanationClient;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationExplanation;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationExplanationInput;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

public final class HttpRecommendationExplanationClient
        implements RecommendationExplanationClient {
    private static final String EXPLANATIONS_PATH = "/api/v1/recommendations/explanations";

    private final RestClient restClient;

    public HttpRecommendationExplanationClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<RecommendationExplanation> generate(
            List<RecommendationExplanationInput> inputs
    ) {
        if (inputs.isEmpty()) {
            return List.of();
        }

        try {
            RecommendationExplanationAiResponse response = restClient.post()
                    .uri(EXPLANATIONS_PATH)
                    .body(new RecommendationExplanationAiRequest(inputs))
                    .retrieve()
                    .body(RecommendationExplanationAiResponse.class);
            if (response == null || response.recommendations() == null) {
                throw new IllegalStateException(
                        "Recommendation AI returned no recommendation explanations"
                );
            }
            return List.copyOf(response.recommendations());
        } catch (ResourceAccessException exception) {
            throw new IllegalStateException("Recommendation AI is unavailable", exception);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "Recommendation AI returned an unsuccessful status",
                    exception
            );
        } catch (HttpMessageConversionException exception) {
            throw new IllegalStateException(
                    "Recommendation AI response could not be decoded",
                    exception
            );
        } catch (RestClientException exception) {
            throw new IllegalStateException("Recommendation AI request failed", exception);
        }
    }
}
