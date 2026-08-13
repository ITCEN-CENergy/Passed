package com.cenergy.passed_backend.domain.coverletter.ai;

import com.cenergy.passed_backend.domain.coverletter.ai.client.CoverLetterAiException;
import com.cenergy.passed_backend.domain.coverletter.ai.client.HttpCoverLetterAiClient;
import com.cenergy.passed_backend.domain.coverletter.ai.dto.CoverLetterAiRequest;
import com.cenergy.passed_backend.domain.coverletter.ai.validation.CoverLetterAiResponseValidator;
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
                          "job_description": "공고"
                        }
                        """))
                .andRespond(withSuccess(validJson(), MediaType.APPLICATION_JSON));

        var result = client.edit(request());

        assertThat(result.qaAlignmentScore()).isEqualTo(84);
        assertThat(result.finalEditedContent()).isEqualTo("수정 답변");
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
        return new CoverLetterAiRequest("질문", "답변", "공고");
    }

    private String validJson() {
        return """
                {
                  "spell_checked_content": "교정 답변",
                  "qa_alignment_score": 84,
                  "qa_alignment_feedback": "문항 피드백",
                  "jd_fit_feedback": "직무 피드백",
                  "final_edited_content": "수정 답변"
                }
                """;
    }
}
