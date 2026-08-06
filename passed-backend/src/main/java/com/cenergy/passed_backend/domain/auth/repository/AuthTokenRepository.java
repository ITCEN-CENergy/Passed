package com.gooroomees.neulbomgil_backend.domain.auth.repository;

import com.gooroomees.neulbomgil_backend.domain.auth.entity.AuthToken;
import com.gooroomees.neulbomgil_backend.domain.auth.entity.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByAuthToken(String authToken);
    Optional<AuthToken> findByAuthTokenAndType(String authToken, TokenType type);
}
