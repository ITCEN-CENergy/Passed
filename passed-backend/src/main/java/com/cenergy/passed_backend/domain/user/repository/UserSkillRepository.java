package com.cenergy.passed_backend.domain.user.repository;

import com.cenergy.passed_backend.domain.skill.entity.UserSkill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {
    @EntityGraph(attributePaths = "skill")
    List<UserSkill> findAllByUserIdOrderBySkill_IdAsc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "skill")
    @Query("""
            SELECT userSkill
            FROM UserSkill userSkill
            WHERE userSkill.user.id = :userId
            ORDER BY userSkill.skill.id
            """)
    List<UserSkill> findAllForUpdateByUserId(@Param("userId") Long userId);
}
