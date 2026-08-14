package com.cenergy.passed_backend.domain.skill.repository;

import com.cenergy.passed_backend.domain.skill.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    List<Skill> findAllByIdIn(Collection<Long> ids);

    @Query("select skill.id as id, skill.name as name from Skill skill order by skill.name asc")
    List<SkillNameView> findAllNames();

    interface SkillNameView {
        Long getId();
        String getName();
    }
}
