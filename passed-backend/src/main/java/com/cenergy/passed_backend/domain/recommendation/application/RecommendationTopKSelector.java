package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.GradedRecommendation;
import com.cenergy.passed_backend.domain.recommendation.application.model.RankedRecommendation;
import com.cenergy.passed_backend.domain.recommendation.entity.RecommendationCandidateTier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

@Component
public class RecommendationTopKSelector {

    // 최종 추천 공고 최대 개수
    public static final int DEFAULT_LIMIT = 12;

    // 추천 공고 정렬 기준
    // 1. 등급 우선순위 높은 순
    // 2. 총점 높은 순
    // 3. PRIMARY 후보 우선
    // 4. 필수 스킬 보유율 높은 순
    // 5. 필수 스킬 숙련도 매칭률 높은 순
    // 6. 중요 스킬 매칭 개수 높은 순
    // 7. 모든 조건이 같으면 공고 ID 오름차순
    private static final Comparator<GradedRecommendation> BEST_FIRST = Comparator
            .comparingInt(GradedRecommendation::gradePriority).reversed()
            .thenComparing(
                    value -> value.score().totalScore(),
                    Comparator.reverseOrder()
            )
            .thenComparingInt(value -> candidateTierOrder(value.score().candidateTier()))
            .thenComparing(
                    value -> value.score().requiredCoverageRate(),
                    Comparator.reverseOrder()
            )
            .thenComparing(
                    value -> value.score().requiredLevelMatchRate(),
                    Comparator.reverseOrder()
            )
            .thenComparing(
                    value -> value.score().importantMatchCount(),
                    Comparator.reverseOrder()
            )
            .thenComparing(value -> value.score().jobPostingId());

    // 기본적으로 상위 12개 추천 공고 선택
    public List<RankedRecommendation> select(
            Collection<GradedRecommendation> candidates
    ) {
        return select(candidates, DEFAULT_LIMIT);
    }

    List<RankedRecommendation> select(
            Collection<GradedRecommendation> candidates,
            int limit
    ) {
        Objects.requireNonNull(candidates, "candidates must not be null");

        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        // 현재까지 선택된 상위 K개 공고를 관리하는 우선순위 큐
        // 큐의 맨 앞(peek)에는 현재 Top K 중 가장 낮은 순위의 공고가 위치
        PriorityQueue<GradedRecommendation> topK = new PriorityQueue<>(
                limit,
                BEST_FIRST.reversed()
        );

        // 모든 후보를 순회하며 상위 K개만 유지
        for (GradedRecommendation candidate : candidates) {

            // 아직 K개가 채워지지 않았다면 그대로 추가
            if (topK.size() < limit) {
                topK.offer(candidate);

                // 현재 후보가 Top K 중 가장 낮은 공고보다 좋다면 교체
            } else if (BEST_FIRST.compare(candidate, topK.peek()) < 0) {
                topK.poll();
                topK.offer(candidate);
            }
        }

        // PriorityQueue는 전체 정렬을 보장하지 않으므로 최종 결과 출력을 위해 BEST_FIRST 기준으로 다시 정렬
        List<GradedRecommendation> sorted = new ArrayList<>(topK);
        sorted.sort(BEST_FIRST);

        // 정렬된 결과에 1위부터 순위 부여
        List<RankedRecommendation> result = new ArrayList<>(sorted.size());

        for (int index = 0; index < sorted.size(); index++) {
            result.add(
                    new RankedRecommendation(
                            sorted.get(index),
                            index + 1
                    )
            );
        }

        // 외부에서 결과 목록을 수정하지 못하도록 불변 List 반환
        return List.copyOf(result);
    }

    // PRIMARY를 FALLBACK보다 먼저 정렬하기 위한 숫자 변환
    private static int candidateTierOrder(
            RecommendationCandidateTier tier
    ) {
        return tier == RecommendationCandidateTier.PRIMARY ? 0 : 1;
    }
}
