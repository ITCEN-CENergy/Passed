package com.gooroomees.neulbomgil_backend.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Builder
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AuthToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String authToken;

    @Enumerated(EnumType.STRING)
    private TokenType type;

    private LocalDateTime expiration;

    public AuthToken(Long userId, String authToken, LocalDateTime expiration, TokenType type) {
        this.userId = userId;
        this.authToken = authToken;
        this.expiration = expiration;
        this.type = type;
    }
}
