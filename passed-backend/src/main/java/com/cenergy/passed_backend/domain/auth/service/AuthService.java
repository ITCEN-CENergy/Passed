package com.cenergy.passed_backend.domain.auth.service;

import com.cenergy.passed_backend.domain.auth.dto.request.LoginRequest;
import com.cenergy.passed_backend.domain.auth.dto.request.PasswordChangeRequest;
import com.cenergy.passed_backend.domain.auth.dto.request.RegisterRequest;
import com.cenergy.passed_backend.domain.auth.dto.request.UpdateUserRequest;
import com.cenergy.passed_backend.domain.auth.dto.request.WithdrawRequest;
import com.cenergy.passed_backend.domain.auth.dto.response.JwtTokenResponse;
import com.cenergy.passed_backend.domain.auth.entity.CustomUserDetails;
import com.cenergy.passed_backend.domain.auth.entity.RefreshToken;
import com.cenergy.passed_backend.domain.auth.repository.RefreshTokenRepository;
import com.cenergy.passed_backend.domain.auth.repository.UserAccountCommandRepository;
import com.cenergy.passed_backend.domain.user.entity.User;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import com.cenergy.passed_backend.global.config.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final UserAccountCommandRepository userAccountCommandRepository;
    private final UserDetailService userDetailService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public String register(RegisterRequest request) {
        String email = UserDetailService.normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        try {
            userAccountCommandRepository.create(
                    request.getName().trim(),
                    email,
                    passwordEncoder.encode(request.getPassword())
            );
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.", exception);
        }
        return "회원가입이 완료되었습니다.";
    }

    @Transactional(readOnly = true)
    public boolean isEmailDuplicated(String email) {
        return userRepository.existsByEmail(UserDetailService.normalizeEmail(email));
    }

    @Transactional
    public JwtTokenResponse login(LoginRequest request) {
        String email = UserDetailService.normalizeEmail(request.getEmail());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        CustomUserDetails principal = userDetailService.loadByEmail(email);
        String accessToken = jwtProvider.generateAccessToken(principal);
        String refreshToken = jwtProvider.generateRefreshToken(principal);
        saveOrRotateRefreshToken(principal.getUserId(), refreshToken);
        return new JwtTokenResponse(accessToken, refreshToken);
    }

    @Transactional
    public JwtTokenResponse refresh(String refreshToken) {
        if (!jwtProvider.isTokenValid(refreshToken) || !jwtProvider.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다.");
        }

        RefreshToken persisted = refreshTokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("폐기되었거나 존재하지 않는 Refresh Token입니다."));
        CustomUserDetails principal = userDetailService.loadById(persisted.getUserId());
        if (!jwtProvider.isValidRefreshToken(refreshToken, principal)) {
            throw new IllegalArgumentException("Refresh Token의 사용자 정보가 일치하지 않습니다.");
        }

        String newAccessToken = jwtProvider.generateAccessToken(principal);
        String newRefreshToken = jwtProvider.generateRefreshToken(principal);
        persisted.update(newRefreshToken);
        return new JwtTokenResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.deleteByRefreshToken(refreshToken);
        }
    }

    @Transactional
    public void changePassword(CustomUserDetails principal, PasswordChangeRequest request) {
        User user = requiredUser(principal.getUserId());
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("기존 비밀번호가 일치하지 않습니다.");
        }
        int updated = userAccountCommandRepository.updatePassword(
                user.getId(),
                passwordEncoder.encode(request.getNewPassword())
        );
        if (updated != 1) {
            throw new IllegalStateException("비밀번호를 변경하지 못했습니다.");
        }
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    @Transactional
    public void updateUserInfo(CustomUserDetails principal, UpdateUserRequest request) {
        int updated = userAccountCommandRepository.updateName(
                principal.getUserId(),
                request.getName().trim()
        );
        if (updated != 1) {
            throw new IllegalArgumentException("존재하지 않는 사용자입니다.");
        }
    }

    @Transactional
    public void withdraw(CustomUserDetails principal, WithdrawRequest request) {
        User user = requiredUser(principal.getUserId());
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        refreshTokenRepository.deleteByUserId(user.getId());
        userRepository.delete(user);
    }

    private User requiredUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

    private void saveOrRotateRefreshToken(Long userId, String refreshToken) {
        RefreshToken persisted = refreshTokenRepository.findByUserId(userId).orElse(null);
        if (persisted == null) {
            refreshTokenRepository.save(new RefreshToken(userId, refreshToken));
        } else {
            persisted.update(refreshToken);
        }
    }
}
