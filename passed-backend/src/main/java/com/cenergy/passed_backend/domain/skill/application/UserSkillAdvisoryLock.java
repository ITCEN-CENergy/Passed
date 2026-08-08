package com.cenergy.passed_backend.domain.skill.application;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class UserSkillAdvisoryLock {
    private final EntityManager entityManager;

    public UserSkillAdvisoryLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Python 재분석 파이프라인과 동일한 두 개의 PostgreSQL advisory-lock 키를 사용한다. */
    public void lock(Long userId) {
        int lockUserId = Math.toIntExact(userId);
        entityManager.createNativeQuery(
                        "SELECT pg_advisory_xact_lock(hashtext('resume_skill_mapping'), CAST(?1 AS INTEGER))"
                )
                .setParameter(1, lockUserId)
                .getSingleResult();
    }
}
