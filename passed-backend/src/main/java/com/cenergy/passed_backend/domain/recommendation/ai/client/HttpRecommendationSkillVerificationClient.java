package com.cenergy.passed_backend.domain.recommendation.ai.client;

import com.cenergy.passed_backend.domain.recommendation.ai.dto.RecommendationSkillVerificationAiRequest;
import com.cenergy.passed_backend.domain.recommendation.ai.dto.RecommendationSkillVerificationAiResponse;
import com.cenergy.passed_backend.domain.recommendation.application.RecommendationSkillVerificationClient;
import com.cenergy.passed_backend.domain.recommendation.application.model.VerifiedSkillMatch;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

public final class HttpRecommendationSkillVerificationClient
        implements RecommendationSkillVerificationClient {
    private static final String VERIFICATION_PATH =
            "/api/v1/recommendations/skill-verifications";

    private final RestClient restClient;

    public HttpRecommendationSkillVerificationClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<VerifiedSkillMatch> verify(Long userId, List<Long> targetSkillIds) {
        if (targetSkillIds.isEmpty()) {
            return List.of();
        }
        try {
            RecommendationSkillVerificationAiResponse response = restClient.post()
                    .uri(VERIFICATION_PATH)
                    .body(new RecommendationSkillVerificationAiRequest(userId, targetSkillIds))
                    .retrieve()
                    .body(RecommendationSkillVerificationAiResponse.class);
            if (response == null || response.verifiedSkills() == null) {
                throw new IllegalStateException("Recommendation AI returned no verified skills");
            }
            return List.copyOf(response.verifiedSkills());
        } catch (ResourceAccessException exception) {
            throw new IllegalStateException("Recommendation skill verifier is unavailable", exception);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "Recommendation skill verifier returned status "
                            + exception.getStatusCode().value()
                            + ": "
                            + exception.getResponseBodyAsString(),
                    exception
            );
        } catch (HttpMessageConversionException exception) {
            throw new IllegalStateException(
                    "Recommendation skill verification response could not be decoded",
                    exception
            );
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Recommendation skill verification request failed",
                    exception
            );
        }
    }
}
