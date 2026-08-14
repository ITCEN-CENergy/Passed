package com.cenergy.passed_backend.domain.skill.repository;

import com.cenergy.passed_backend.domain.skill.entity.UserSkillExtractionRun;
import com.cenergy.passed_backend.domain.skill.entity.UserSkillExtractionRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSkillExtractionRunRepository extends JpaRepository<UserSkillExtractionRun, Long> {
    Optional<UserSkillExtractionRun> findFirstByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId,
            UserSkillExtractionRunStatus status
    );

    Optional<UserSkillExtractionRun> findByIdAndUserId(Long id, Long userId);
}
