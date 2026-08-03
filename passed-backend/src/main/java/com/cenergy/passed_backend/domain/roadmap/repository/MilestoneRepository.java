package com.cenergy.passed_backend.domain.roadmap.repository;

import com.cenergy.passed_backend.roadmap.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findAllByIdInOrderByIdAsc(Collection<Long> milestoneIds);
}
