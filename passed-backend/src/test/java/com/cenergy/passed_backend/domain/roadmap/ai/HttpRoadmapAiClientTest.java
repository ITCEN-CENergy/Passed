package com.cenergy.passed_backend.domain.roadmap.ai;

import com.cenergy.passed_backend.domain.roadmap.ai.client.HttpRoadmapAiClient;
import com.cenergy.passed_backend.domain.roadmap.ai.client.RoadmapAiException;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapAiRequest;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapAiResult;
import com.cenergy.passed_backend.domain.roadmap.ai.validation.RoadmapAiResponseValidator;
import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpRoadmapAiClientTest {
    private static final String URL = "http://localhost:8000/api/v1/roadmaps/generate";

    @Test
    void postsRequestAndReturnsValidatedResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRoadmapAiClient client = client(builder.build());
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("priorityScore"))))
                .andRespond(withSuccess(validJson(), MediaType.APPLICATION_JSON));

        ValidatedRoadmapAiResult result = client.generate(request());

        assertThat(result.skills()).singleElement().satisfies(skill ->
                assertThat(skill.milestones()).hasSize(6));
        server.verify();
    }

    @Test
    void mapsHttp4xxToInvalidResponse() {
        assertHttpError(withBadRequest(), ErrorCode.ROADMAP_AI_INVALID_RESPONSE);
    }

    @Test
    void mapsHttp5xxToUnavailable() {
        assertHttpError(withServerError(), ErrorCode.ROADMAP_AI_UNAVAILABLE);
    }

    @Test
    void mapsMalformedJsonToInvalidResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess("{invalid", MediaType.APPLICATION_JSON));

        assertError(client(builder.build()), ErrorCode.ROADMAP_AI_INVALID_RESPONSE);
    }

    @Test
    void mapsUnsupportedEnumToInvalidResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess(
                validJson().replace("\"CONCEPT\"", "\"UNKNOWN\""), MediaType.APPLICATION_JSON));

        assertError(client(builder.build()), ErrorCode.ROADMAP_AI_INVALID_RESPONSE);
    }

    @Test
    void mapsMissingRequiredFieldToInvalidResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess(
                validJson().replace("\"estimatedMinutes\": 120,", ""), MediaType.APPLICATION_JSON));

        assertError(client(builder.build()), ErrorCode.ROADMAP_AI_INVALID_RESPONSE);
    }

    @Test
    void mapsConnectTimeoutToTimeout() {
        assertTransportError(new SocketTimeoutException("connect timed out"), ErrorCode.ROADMAP_AI_TIMEOUT);
    }

    @Test
    void mapsReadTimeoutToTimeout() {
        assertTransportError(new SocketTimeoutException("read timed out"), ErrorCode.ROADMAP_AI_TIMEOUT);
    }

    @Test
    void mapsConnectionFailureToUnavailable() {
        assertTransportError(new ConnectException("connection refused"), ErrorCode.ROADMAP_AI_UNAVAILABLE);
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

    private void assertError(HttpRoadmapAiClient client, ErrorCode expected) {
        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOf(RoadmapAiException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    private HttpRoadmapAiClient client(RestClient restClient) {
        return new HttpRoadmapAiClient(restClient, new RoadmapAiResponseValidator());
    }

    private RoadmapAiRequest request() {
        return new RoadmapAiRequest(10L, List.of(new RoadmapAiRequest.Competency(
                "competency-1", 1L, "Docker", CompetencyCategory.TECHNICAL_SKILL,
                1, 3, RequirementType.REQUIRED, 2, 2, 1,
                List.of(new RoadmapAiRequest.Source(101L, "Docker 실습 경험"))
        )));
    }

    private String validJson() {
        return """
                {
                  "title": "개인 맞춤 역량 강화 로드맵",
                  "skills": [{
                    "roadmapSkillKey": "competency-1",
                    "milestones": [
                      {
                        "title": "Docker 목표 수준 2 학습",
                        "description": "설명",
                        "learningObjective": "목표",
                        "completionCriteria": "기준",
                        "startLevel": 1,
                        "targetLevel": 2,
                        "milestoneType": "CONCEPT",
                        "difficulty": "BEGINNER",
                        "estimatedMinutes": 120,
                        "learningOrder": 1
                      },
                      {
                        "title": "Docker 목표 수준 2 실습",
                        "description": "설명",
                        "learningObjective": "목표",
                        "completionCriteria": "기준",
                        "startLevel": 1,
                        "targetLevel": 2,
                        "milestoneType": "PRACTICE",
                        "difficulty": "INTERMEDIATE",
                        "estimatedMinutes": 120,
                        "learningOrder": 2
                      },
                      {
                        "title": "Docker 목표 수준 2 평가",
                        "description": "설명",
                        "learningObjective": "목표",
                        "completionCriteria": "기준",
                        "startLevel": 1,
                        "targetLevel": 2,
                        "milestoneType": "ASSESSMENT",
                        "difficulty": "INTERMEDIATE",
                        "estimatedMinutes": 120,
                        "learningOrder": 3
                      },
                      {
                        "title": "Docker 목표 수준 3 학습",
                        "description": "설명",
                        "learningObjective": "목표",
                        "completionCriteria": "기준",
                        "startLevel": 2,
                        "targetLevel": 3,
                        "milestoneType": "PROJECT",
                        "difficulty": "ADVANCED",
                        "estimatedMinutes": 180,
                        "learningOrder": 4
                      },
                      {
                        "title": "Docker 목표 수준 3 실습",
                        "description": "설명",
                        "learningObjective": "목표",
                        "completionCriteria": "기준",
                        "startLevel": 2,
                        "targetLevel": 3,
                        "milestoneType": "PROJECT",
                        "difficulty": "ADVANCED",
                        "estimatedMinutes": 180,
                        "learningOrder": 5
                      },
                      {
                        "title": "Docker 목표 수준 3 평가",
                        "description": "설명",
                        "learningObjective": "목표",
                        "completionCriteria": "기준",
                        "startLevel": 2,
                        "targetLevel": 3,
                        "milestoneType": "ASSESSMENT",
                        "difficulty": "ADVANCED",
                        "estimatedMinutes": 180,
                        "learningOrder": 6
                      }
                    ]
                  }]
                }
                """;
    }
}
