package com.cenergy.passed_backend.domain.skillgap;

import com.cenergy.passed_backend.domain.skillgap.application.JpaLearningCompetencyService;
import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 DB에 저장된 추천 결과를 기준으로 JpaLearningCompetencyService를 검증한다.
 *
 * 선행 조건 (아래 순서로 실행되어 있어야 함):
 *   1. POST /api/v1/users/me/skill-extractions
 *   2. PUT  /api/v1/users/me/skills/preferences
 *   3. POST /api/v1/recommendations/runs
 *
 * TEST_JOB_POSTING_ID는 위 3번 실행 후 아래 쿼리로 확인한 값이다.
 * 추천을 다시 실행하면 Top 12가 달라질 수 있으므로 그때는 이 값을 갱신해야 한다.
 *
 *   SELECT jr.job_posting_id, jr.rank_order
 *   FROM job_recommendations jr
 *   JOIN recommendation_runs rr ON rr.id = jr.recommendation_run_id
 *   WHERE rr.user_id = 56
 *   ORDER BY rr.started_at DESC, jr.rank_order;
 *
 * 주의: LearningCompetencyService 인터페이스로 주입하면 @Primary가 붙은
 * AiLearningCompetencyService가 주입되므로, 반드시 구체 클래스로 선언한다.
 */
@SpringBootTest
class JpaLearningCompetencyServiceE2ETest {

    private static final Long TEST_USER_ID = 56L;
    private static final Long TEST_JOB_POSTING_ID = 4682L;

    @Autowired
    private JpaLearningCompetencyService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsLearningCompetencyResponseUsingRealRecommendationResult() throws Exception {
        // when - 파라미터 순서 주의: (공고 ID, 사용자 ID)
        LearningCompetencyResponse response =
                service.getLearningCompetencies(TEST_JOB_POSTING_ID, TEST_USER_ID);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(TEST_USER_ID);
        assertThat(response.jobPostingId()).isEqualTo(TEST_JOB_POSTING_ID);
        assertThat(response.competencies()).isNotNull();
        assertThat(response.competencies()).isNotEmpty();

        assertThat(response.competencies()).allSatisfy(item -> {
            assertThat(item.standardCompetencyId()).isPositive();
            assertThat(item.standardCompetencyName()).isNotBlank();
            assertThat(item.category()).isNotNull();
            assertThat(item.requirementType()).isNotNull();
            assertThat(item.targetLevel()).isBetween(1, 3);

            // 미보유 스킬은 currentLevel이 null일 수 있다
            if (item.currentLevel() != null) {
                assertThat(item.currentLevel()).isBetween(1, 3);
            }
        });

        System.out.println("=== userId=" + TEST_USER_ID
                + ", jobPostingId=" + TEST_JOB_POSTING_ID + " 응답 ===");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response)
        );
    }
}