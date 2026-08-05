package com.cenergy.passed_backend.domain.roadmap.repository;

import com.cenergy.passed_backend.domain.roadmap.entity.ResourceRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ResourceRecommendationRepository extends JpaRepository<ResourceRecommendation, Long> {
    List<ResourceRecommendation> findAllByMilestoneIdInOrderByMilestoneIdAscRankOrderAsc(Collection<Long> milestoneIds);
}
