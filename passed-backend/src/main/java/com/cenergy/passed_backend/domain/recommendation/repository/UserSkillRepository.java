package com.cenergy.passed_backend.domain.recommendation.repository;

import com.cenergy.passed_backend.skill.entity.UserSkill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {
    @EntityGraph(attributePaths = "skill")
    List<UserSkill> findAllByUserIdOrderBySkill_IdAsc(Long userId);
}
