package com.cenergy.passed_backend.domain.skill.ai;

import com.cenergy.passed_backend.domain.skill.ai.client.HttpUserSkillAiClient;
import com.cenergy.passed_backend.domain.skill.ai.client.UserSkillAiException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpUserSkillAiClientTest {
    private static final String URL = "http://localhost:8000/api/v1/user-skills/extractions";

    @Test
    void postsCurrentUserAndReturnsPersistedResult() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpUserSkillAiClient client = new HttpUserSkillAiClient(builder.build());
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"userId\":257}"))
                .andRespond(withSuccess(validJson(), MediaType.APPLICATION_JSON));

        var response = client.extract(257L);

        assertThat(response.skillCount()).isEqualTo(5);
        assertThat(response.persisted()).isTrue();
        server.verify();
    }

    @Test
    void rejectsAResponseForAnotherUser() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess(
                validJson().replace("\"userId\":257", "\"userId\":999"),
                MediaType.APPLICATION_JSON
        ));

        assertError(new HttpUserSkillAiClient(builder.build()), ErrorCode.USER_SKILL_AI_INVALID_RESPONSE);
    }

    @Test
    void mapsServerFailureToUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertError(new HttpUserSkillAiClient(builder.build()), ErrorCode.USER_SKILL_AI_UNAVAILABLE);
    }

    @Test
    void mapsTimeoutAndConnectionFailure() {
        assertTransportError(new SocketTimeoutException("read timed out"), ErrorCode.USER_SKILL_AI_TIMEOUT);
        assertTransportError(new ConnectException("connection refused"), ErrorCode.USER_SKILL_AI_UNAVAILABLE);
    }

    private void assertTransportError(IOException cause, ErrorCode expected) {
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:8000")
                .requestInterceptor((request, body, execution) -> {
                    throw new ResourceAccessException("request failed", cause);
                })
                .build();
        assertError(new HttpUserSkillAiClient(client), expected);
    }

    private void assertError(HttpUserSkillAiClient client, ErrorCode expected) {
        assertThatThrownBy(() -> client.extract(257L))
                .isInstanceOf(UserSkillAiException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    private String validJson() {
        return """
                {
                  "userId":257,
                  "processedChunkCount":7,
                  "skillCount":5,
                  "unmappedCount":1,
                  "persisted":true,
                  "resumeChunksEmbedded":3,
                  "coverLetterChunksEmbedded":2
                }
                """;
    }
}
