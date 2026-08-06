package com.gooroomees.neulbomgil_backend.domain.auth.service;

import com.gooroomees.neulbomgil_backend.domain.auth.entity.RefreshToken;
import com.gooroomees.neulbomgil_backend.domain.auth.repository.RefreshTokenRepository;
import com.gooroomees.neulbomgil_backend.global.config.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;

    public RefreshToken findByRefreshToken(String refreshToken) {
        return refreshTokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 Refresh 토큰입니다."));
    }

    public void delete(String refreshToken) {

        Long userId = jwtProvider.extractUserId(refreshToken);

        refreshTokenRepository.deleteById(userId);
    }
}
