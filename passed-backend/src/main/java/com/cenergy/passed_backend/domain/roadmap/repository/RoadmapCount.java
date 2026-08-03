package com.cenergy.passed_backend.domain.roadmap.repository;

/**
 * Roadmap id별 연관 데이터 개수 집계 projection.
 */
public interface RoadmapCount {
    Long getRoadmapId();

    long getCount();
}
