package com.cenergy.passed_backend.domain.recommendation.ai;

import com.cenergy.passed_backend.domain.recommendation.ai.client.HttpRecommendationExplanationClient;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationExplanation;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationExplanationInput;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpRecommendationExplanationClientTest {
    private static final String URL =
            "http://localhost:8000/api/v1/recommendations/explanations";

    @Test
    void postsInputsToPythonApiAndReturnsExplanations() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRecommendationExplanationClient client =
                new HttpRecommendationExplanationClient(builder.build());
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"jobPostingId\":101"
                )))
                .andRespond(withSuccess("""
                        {
                          "recommendations": [{
                            "jobPostingId": 101,
                            "reason": "추천 근거",
                            "strengths": "Java 역량",
                            "weaknesses": "AWS 보완 필요"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RecommendationExplanation> result = client.generate(List.of(input()));

        assertThat(result).containsExactly(new RecommendationExplanation(
                101L,
                "추천 근거",
                "Java 역량",
                "AWS 보완 필요"
        ));
        server.verify();
    }

    @Test
    void doesNotCallPythonApiForEmptyInput() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRecommendationExplanationClient client =
                new HttpRecommendationExplanationClient(builder.build());

        assertThat(client.generate(List.of())).isEmpty();
        server.verify();
    }

    @Test
    void wrapsUnsuccessfulPythonApiResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRecommendationExplanationClient client =
                new HttpRecommendationExplanationClient(builder.build());
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.generate(List.of(input())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Recommendation AI returned an unsuccessful status");
    }

    private RecommendationExplanationInput input() {
        RecommendationExplanationInput.SkillFact strength =
                new RecommendationExplanationInput.SkillFact(
                        "Java",
                        "REQUIRED",
                        "LEVEL",
                        (short) 2,
                        (short) 3,
                        "0.6667",
                        true,
                        false
                );
        return new RecommendationExplanationInput(
                101L,
                "백엔드 개발자",
                "Passed",
                1,
                "RECOMMENDED",
                "PRIMARY",
                "75.0000",
                "70.0000",
                "5.0000",
                "0.0000",
                "0.0000",
                "0.8000",
                "0.7000",
                1,
                List.of(strength),
                List.of()
        );
    }
}
