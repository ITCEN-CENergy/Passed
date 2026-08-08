package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationExplanation;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationExplanationInput;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponseCreateParams;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class OpenAiRecommendationExplanationClient implements RecommendationExplanationClient {
    static final String MODEL = "gpt-4o-mini";

    private static final String INSTRUCTION = """
            당신은 채용 공고 추천 결과 설명기입니다.
            입력에 포함된 점수, 등급, 순위와 스킬 사실만 사용하세요.
            점수, 등급, 순위를 변경하거나 입력에 없는 경력과 스킬을 추측하지 마세요.
            각 공고마다 한국어로 다음 내용을 작성하세요.
            - reason: 추천 등급과 핵심 근거를 2~3문장으로 설명
            - strengths: 실제 보유 또는 충족 스킬을 중심으로 한 강점 설명
            - weaknesses: 미보유 또는 요구 수준 미달 스킬을 중심으로 한 보완점 설명
            요청받은 모든 jobPostingId를 정확히 한 번씩 반환하세요.

            추천 결과 입력:
            """;

    private final ObjectMapper objectMapper;
    private volatile OpenAIClient client;

    public OpenAiRecommendationExplanationClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<RecommendationExplanation> generate(List<RecommendationExplanationInput> inputs) {
        if (inputs.isEmpty()) {
            return List.of();
        }
        StructuredResponseCreateParams<ExplanationBatchOutput> params = ResponseCreateParams
                .builder()
                .input(INSTRUCTION + serialize(inputs))
                .text(ExplanationBatchOutput.class)
                .model(MODEL)
                .build();

        ExplanationBatchOutput output = client().responses().create(params).output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "OpenAI returned no structured recommendation explanation"
                ));
        if (output.recommendations == null) {
            throw new IllegalStateException("OpenAI returned no recommendation items");
        }
        return output.recommendations.stream()
                .map(value -> new RecommendationExplanation(
                        value.jobPostingId,
                        value.reason,
                        value.strengths,
                        value.weaknesses
                ))
                .toList();
    }

    private String serialize(List<RecommendationExplanationInput> inputs) {
        try {
            return objectMapper.writeValueAsString(inputs);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize recommendation explanation input", exception);
        }
    }

    private OpenAIClient client() {
        OpenAIClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = OpenAIOkHttpClient.fromEnv();
                    client = current;
                }
            }
        }
        return current;
    }

    public static final class ExplanationBatchOutput {
        public List<ExplanationItemOutput> recommendations;
    }

    public static final class ExplanationItemOutput {
        public long jobPostingId;
        public String reason;
        public String strengths;
        public String weaknesses;
    }
}
