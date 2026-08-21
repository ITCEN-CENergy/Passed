package com.cenergy.passed_backend.domain.coverletter.ai;

import com.cenergy.passed_backend.domain.coverletter.ai.exception.CoverLetterAiException;
import com.cenergy.passed_backend.domain.coverletter.ai.client.HttpCoverLetterAiClient;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterReviewAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterUserSkill;
import com.cenergy.passed_backend.domain.coverletter.ai.validation.CoverLetterAiResponseValidator;
import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpCoverLetterAiClientTest {
    private static final String URL = "http://localhost:8000/coverletter/edit";
    private static final String SUGGEST_URL = "http://localhost:8000/coverletter/suggest";
    private static final String REVIEW_URL = "http://localhost:8000/coverletter/review";

    @Test
    void postsSnakeCaseRequestAndReturnsValidatedResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCoverLetterAiClient client = client(builder.build());
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "question": "질문",
                          "content": "답변",
                          "job_description": "공고",
                          "company_talent_profile": "도전과 협업",
                          "character_limit": 700,
                          "user_skills": [
                            {
                              "skill_id": 9,
                              "name": "Spring Boot",
                              "category": "TECHNICAL_SKILL",
                              "level": 2
                            }
                          ]
                        }
                        """))
                .andRespond(withSuccess(validJson(), MediaType.APPLICATION_JSON));

        var result = client.edit(request());

        assertThat(result.qaAlignmentScore()).isEqualTo(84);
        assertThat(result.shortcomings()).isEqualTo("미흡한 부분");
        assertThat(result.recommendedRevisionDirection()).isEqualTo("추천 수정 방향");
        server.verify();
    }

    @Test
    void generatesSuggestedAnswerThroughSeparateEndpoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCoverLetterAiClient client = client(builder.build());
        server.expect(requestTo(SUGGEST_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "question": "질문",
                          "content": "답변",
                          "job_description": "공고",
                          "company_talent_profile": "도전과 협업",
                          "character_limit": 700,
                          "user_skills": [
                            {
                              "skill_id": 9,
                              "name": "Spring Boot",
                              "category": "TECHNICAL_SKILL",
                              "level": 2
                            }
                          ]
                        }
                        """))
                .andRespond(withSuccess("""
                        {"suggested_answer":"추천 수정안"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.suggest(request())).isEqualTo("추천 수정안");
        server.verify();
    }

    @Test
    void postsCompanyTalentProfileForOverallReview() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCoverLetterAiClient client = client(builder.build());
        server.expect(requestTo(REVIEW_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "items": [{
                            "item_id": 12,
                            "display_order": 1,
                            "question": "질문",
                            "content": "답변",
                            "character_limit": 700
                          }],
                          "job_description": "공고",
                          "company_talent_profile": "도전과 협업",
                          "user_skills": []
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "overall_feedback": {
                            "overall_score": 84,
                            "summary": "요약",
                            "strengths": "강점",
                            "improvements": "개선점"
                          },
                          "items": []
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.review(new CoverLetterReviewAiRequest(
                java.util.List.of(new CoverLetterReviewAiRequest.Item(
                        12L, 1, "질문", "답변", 700
                )),
                "공고",
                "도전과 협업",
                java.util.List.of()
        ));

        assertThat(response.overallFeedback().overallScore()).isEqualTo(84);
        server.verify();
    }

    @Test
    void mapsHttp4xxToInvalidResponse() {
        assertHttpError(withBadRequest(), ErrorCode.COVER_LETTER_AI_INVALID_RESPONSE);
    }

    @Test
    void mapsHttp5xxToUnavailable() {
        assertHttpError(withServerError(), ErrorCode.COVER_LETTER_AI_UNAVAILABLE);
    }

    @Test
    void mapsMalformedJsonToInvalidResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess("{invalid", MediaType.APPLICATION_JSON));

        assertError(client(builder.build()), ErrorCode.COVER_LETTER_AI_INVALID_RESPONSE);
    }

    @Test
    void mapsTimeoutAndConnectionFailure() {
        assertTransportError(new SocketTimeoutException("read timed out"), ErrorCode.COVER_LETTER_AI_TIMEOUT);
        assertTransportError(new ConnectException("connection refused"), ErrorCode.COVER_LETTER_AI_UNAVAILABLE);
    }

    private void assertHttpError(
            org.springframework.test.web.client.ResponseCreator responseCreator,
            ErrorCode expected
    ) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(responseCreator);
        assertError(client(builder.build()), expected);
    }

    private void assertTransportError(IOException cause, ErrorCode expected) {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:8000")
                .requestInterceptor((request, body, execution) -> {
                    throw new ResourceAccessException("request failed", cause);
                })
                .build();
        assertError(client(restClient), expected);
    }

    private void assertError(HttpCoverLetterAiClient client, ErrorCode expected) {
        assertThatThrownBy(() -> client.edit(request()))
                .isInstanceOf(CoverLetterAiException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    private HttpCoverLetterAiClient client(RestClient restClient) {
        return new HttpCoverLetterAiClient(restClient, new CoverLetterAiResponseValidator());
    }

    private CoverLetterAiRequest request() {
        return new CoverLetterAiRequest(
                "질문",
                "답변",
                "공고",
                "도전과 협업",
                java.util.List.of(new CoverLetterUserSkill(
                        9L, "Spring Boot", SkillCategory.TECHNICAL_SKILL, (short) 2
                )),
                700
        );
    }

    private String validJson() {
        return """
                {
                  "qa_alignment_score": 84,
                  "shortcomings": "미흡한 부분",
                  "recommended_revision_direction": "추천 수정 방향"
                }
                """;
    }
}
