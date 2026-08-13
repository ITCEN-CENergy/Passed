package com.cenergy.passed_backend.domain.auth.repository;

import com.cenergy.passed_backend.domain.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByRefreshToken(String refreshToken);
    long deleteByUserId(Long userId);
    long deleteByRefreshToken(String refreshToken);
}
