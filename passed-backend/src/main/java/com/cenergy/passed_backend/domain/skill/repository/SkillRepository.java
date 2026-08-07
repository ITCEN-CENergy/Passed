package com.cenergy.passed_backend.domain.skill.repository;

import com.cenergy.passed_backend.domain.skill.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    List<Skill> findAllByIdIn(Collection<Long> ids);
}
