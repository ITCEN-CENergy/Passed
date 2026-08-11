package com.cenergy.passed_backend.domain.auth.application;

import com.cenergy.passed_backend.domain.auth.dto.request.LoginRequest;
import com.cenergy.passed_backend.domain.auth.dto.request.PasswordChangeRequest;
import com.cenergy.passed_backend.domain.auth.dto.request.RegisterRequest;
import com.cenergy.passed_backend.domain.auth.dto.request.UpdateUserRequest;
import com.cenergy.passed_backend.domain.auth.dto.request.WithdrawRequest;
import com.cenergy.passed_backend.domain.auth.dto.response.JwtTokenResponse;
import com.cenergy.passed_backend.domain.auth.entity.CustomUserDetails;
import com.cenergy.passed_backend.domain.auth.entity.RefreshToken;
import com.cenergy.passed_backend.domain.auth.repository.RefreshTokenRepository;
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
    private final CustomUserDetailService customUserDetailService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public String register(RegisterRequest request) {
        String email = CustomUserDetailService.normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        try {
            User user = User.builder()
                    .name(request.getName().trim())
                    .email(email)
                    .password(passwordEncoder.encode(request.getPassword()))
                    .build();
            // Flush inside this boundary so a concurrent duplicate signup is
            // translated to the public duplicate-email response here.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.", exception);
        }
        return "회원가입이 완료되었습니다.";
    }

    @Transactional(readOnly = true)
    public boolean isEmailDuplicated(String email) {
        return userRepository.existsByEmail(CustomUserDetailService.normalizeEmail(email));
    }

    @Transactional
    public JwtTokenResponse login(LoginRequest request) {
        String email = CustomUserDetailService.normalizeEmail(request.getEmail());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        CustomUserDetails principal = customUserDetailService.loadByEmail(email);
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
        CustomUserDetails principal = customUserDetailService.loadById(persisted.getUserId());
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
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    @Transactional
    public void updateUserInfo(CustomUserDetails principal, UpdateUserRequest request) {
        User user = requiredUser(principal.getUserId());
        user.setName(request.getName().trim());
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
