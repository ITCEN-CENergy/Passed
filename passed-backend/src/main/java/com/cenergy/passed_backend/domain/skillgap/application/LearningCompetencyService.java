package com.cenergy.passed_backend.domain.skillgap.application;

import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;

/**
 * 로드맵에서 학습할 역량 조회 구현을 교체할 수 있게 하는 애플리케이션 계약이다.
 */
public interface LearningCompetencyService {
    LearningCompetencyResponse getLearningCompetencies(Long jobPostingId, Long userId);
}
