package com.cenergy.passed_backend.domain.recommendation.repository;

import com.cenergy.passed_backend.skill.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RecommendationSkillRepository extends JpaRepository<Skill, Long> {
    List<Skill> findAllByIdIn(Collection<Long> ids);
}
