package com.cenergy.passed_backend.domain.auth.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 기존 User 엔티티를 변경하지 않고 계정 쓰기 작업을 수행하는 인증 전용 어댑터다.
 */
@Repository
@RequiredArgsConstructor
public class UserAccountCommandRepository {

    private final EntityManager entityManager;

    public Long create(String name, String email, String encodedPassword) {
        Number id = (Number) entityManager.createNativeQuery("""
                        INSERT INTO users (name, email, password)
                        VALUES (:name, :email, :password)
                        RETURNING id
                        """)
                .setParameter("name", name)
                .setParameter("email", email)
                .setParameter("password", encodedPassword)
                .getSingleResult();
        return id.longValue();
    }

    public int updatePassword(Long userId, String encodedPassword) {
        return entityManager.createQuery("""
                        UPDATE User u
                           SET u.password = :password
                         WHERE u.id = :userId
                        """)
                .setParameter("password", encodedPassword)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    public int updateName(Long userId, String name) {
        return entityManager.createQuery("""
                        UPDATE User u
                           SET u.name = :name
                         WHERE u.id = :userId
                        """)
                .setParameter("name", name)
                .setParameter("userId", userId)
                .executeUpdate();
    }
}
