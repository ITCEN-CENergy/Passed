package com.cenergy.passed_backend.domain.roadmap.repository;

import com.cenergy.passed_backend.domain.roadmap.entity.LearningProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningProgressRepository extends JpaRepository<LearningProgress, Long> {
    List<LearningProgress> findAllByMilestoneIdOrderByRecordedAtDescIdDesc(Long milestoneId);
}
