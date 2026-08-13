package com.cenergy.passed_backend.domain.skillgap;

import com.cenergy.passed_backend.domain.skillgap.ai.client.HttpLearningCompetencyAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpLearningCompetencyAiClientTest {
    private static final String URL =
            "http://localhost:8000/api/v1/skill-gaps/learning-competencies";

    @Test
    void postsIdsAndKeepsLearningCompetencyContract() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpLearningCompetencyAiClient client = new HttpLearningCompetencyAiClient(builder.build());
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"userId\":257,\"jobPostingId\":101}"))
                .andRespond(withSuccess(validJson(), MediaType.APPLICATION_JSON));

        var response = client.getLearningCompetencies(101L, 257L);

        assertThat(response.competencies()).hasSize(2);
        assertThat(response.competencies().get(0).standardCompetencyName()).isEqualTo("Docker");
        assertThat(response.competencies().get(1).currentLevel()).isEqualTo(3);
        server.verify();
    }

    private String validJson() {
        return """
                {
                  "userId": 257,
                  "jobPostingId": 101,
                  "competencies": [
                    {
                      "standardCompetencyId": 10,
                      "standardCompetencyName": "Docker",
                      "category": "TECHNICAL_SKILL",
                      "requirementType": "REQUIRED",
                      "currentLevel": 1,
                      "targetLevel": 3,
                      "currentLevelEvidence": "Docker local usage"
                    },
                    {
                      "standardCompetencyId": 20,
                      "standardCompetencyName": "Java",
                      "category": "TECHNICAL_SKILL",
                      "requirementType": "REQUIRED",
                      "currentLevel": 3,
                      "targetLevel": 3,
                      "currentLevelEvidence": "Spring Boot project"
                    }
                  ]
                }
                """;
    }
}
