package com.gooroomees.neulbomgil_backend.domain.auth.service;

import com.gooroomees.neulbomgil_backend.domain.auth.dto.request.*;
import com.gooroomees.neulbomgil_backend.domain.auth.dto.response.JwtTokenResponse;
import com.gooroomees.neulbomgil_backend.domain.auth.dto.response.KakaoProfileResponse;
import com.gooroomees.neulbomgil_backend.domain.auth.dto.response.KakaoTokenResponse;
import com.gooroomees.neulbomgil_backend.domain.auth.entity.*;
import com.gooroomees.neulbomgil_backend.domain.auth.repository.AuthTokenRepository;
import com.gooroomees.neulbomgil_backend.domain.auth.repository.RefreshTokenRepository;
import com.gooroomees.neulbomgil_backend.domain.auth.repository.UserAuthRepository;
import com.gooroomees.neulbomgil_backend.global.config.JwtProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final RefreshTokenService refreshTokenService;
    private final UserAuthRepository userAuthRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserAuthService userAuthService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailService emailService;
    private final AuthTokenRepository authTokenRepository;

    @Value("${kakao.auth.client}")
    private String kakaoKey;
    @Value("${kakao.auth.client_secret_key}")
    private String clientSecretKey;

    @Transactional
    public String register(RegisterRequest registerRequest) {
        if (userAuthRepository.existsByEmail(registerRequest.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        UserAuth user = UserAuth.builder()
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .name(registerRequest.getName())
                .role(Role.USER)
                .status(Status.INACTIVE)
                .build();
        userAuthRepository.save(user);
        emailService.sendAuthLink(user.getUserId());

        return "User registered. Please check your email for verification.";
    }

    public boolean isEmailDuplicated(String email) {
        return userAuthRepository.existsByEmail(email);
    }

    @Transactional
    public void verifyEmail(String token) {
        AuthToken authToken = authTokenRepository.findByAuthTokenAndType(token, TokenType.SIGNUP)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 인증 토큰입니다."));

        if (authToken.getExpiration().isBefore(LocalDateTime.now())) {
            authTokenRepository.delete(authToken);
            throw new IllegalArgumentException("만료된 인증 토큰입니다.");
        }

        UserAuth user = userAuthService.findById(authToken.getUserId());
        if (authToken.getType() == TokenType.SIGNUP) {
            user.activate();
            userAuthRepository.save(user);
            authTokenRepository.delete(authToken);
        } else if (authToken.getType() == TokenType.PASSWORD_RESET) {
            // UserAuth user = userAuthService.findById(authToken.getUserId());

            // 비밀번호 교체 로직


            userAuthRepository.save(user);
            authTokenRepository.delete(authToken);
        }
    }

    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        UserAuth user = userAuthRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일입니다."));
        emailService.sendPasswordResetLink(user.getUserId());

    }

    @Transactional
    public void resetPassword(PasswordUpdateRequest request) {
        AuthToken authToken = authTokenRepository.findByAuthTokenAndType(request.getToken(), TokenType.PASSWORD_RESET)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 비밀번호 초기화 토큰입니다."));

        if (authToken.getExpiration().isBefore(LocalDateTime.now())) {
            authTokenRepository.delete(authToken);
            throw new IllegalArgumentException("만료된 인증 토큰입니다.");
        }

        UserAuth user = userAuthService.findById(authToken.getUserId());
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        userAuthRepository.save(user);
        authTokenRepository.delete(authToken);
    }

    public JwtTokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        UserAuth user = userAuthRepository.findByEmail(request.getEmail()).orElseThrow();
        if (!user.getStatus().equals(Status.ACTIVE)) {
            throw new RuntimeException("Account is not active");
        }

        String accessToken = jwtProvider.generateAccessToken(user);
        String refreshToken = jwtProvider.generateRefreshToken(user);

        RefreshToken userRefreshToken = refreshTokenRepository.findByUserId(user.getUserId()).orElse(null);
        if (userRefreshToken == null) {
            refreshTokenRepository.save(new RefreshToken(user.getUserId(), refreshToken));
        } else {
            refreshTokenRepository.deleteById(userRefreshToken.getId());
            refreshTokenRepository.save(new RefreshToken(user.getUserId(), refreshToken));
        }

        return JwtTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public void logout(String refreshToken) {
        if (!jwtProvider.isTokenValid(refreshToken))
            throw new IllegalArgumentException("토큰이 아닌 값이 넘어옴");

        if (!jwtProvider.isRefreshToken(refreshToken))
            throw new IllegalArgumentException("해당 토큰은 refresh 토큰이 아님");

        Long userId = jwtProvider.extractUserId(refreshToken);
        refreshTokenRepository.deleteById(userId);
    }

    public String createNewAccessToken(String refreshToken) {
        if (!jwtProvider.isTokenValid(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token");
        }

        Long userId = refreshTokenService.findByRefreshToken(refreshToken).getUserId();
        UserAuth user = userAuthService.findById(userId);

        return jwtProvider.generateAccessToken(user);
    }

    // 카카오 로그인
    public JwtTokenResponse kakaoOAuthLogin(String accessCode, HttpServletResponse httpServletResponse) {
        KakaoTokenResponse kakaoToken = requestToken(accessCode);
        KakaoProfileResponse kakaoProfile = requestProfile(kakaoToken);

        UserAuth user = userAuthRepository.findByEmail(kakaoProfile.getKakao_account().getEmail()).orElse(null);
        if (user == null) {
            String nickname = null;
            if (kakaoProfile.getKakao_account() != null && kakaoProfile.getKakao_account().getProfile() != null) {
                nickname = kakaoProfile.getKakao_account().getProfile().getNickname();
            }
            if (nickname == null && kakaoProfile.getProperties() != null) {
                nickname = kakaoProfile.getProperties().getNickname();
            }
            if (nickname == null) {
                nickname = "Kakao User";
            }
            user = UserAuth.builder()
                    .email(kakaoProfile.getKakao_account().getEmail())
                    .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                    .name(nickname)
                    .role(Role.USER)
                    .status(Status.ACTIVE)
                    .build();
            userAuthRepository.save(user);
        }

        if (!user.getStatus().equals(Status.ACTIVE)) {
            // throw new RuntimeException("Account is not active");
            return null;
        }

        String accessToken = jwtProvider.generateAccessToken(user);
        String refreshToken = jwtProvider.generateRefreshToken(user);

        RefreshToken userRefreshToken = refreshTokenRepository.findByUserId(user.getUserId()).orElse(null);
        if (userRefreshToken == null) {
            refreshTokenRepository.save(new RefreshToken(user.getUserId(), refreshToken));
        } else {
            refreshTokenRepository.deleteById(userRefreshToken.getId());
            refreshTokenRepository.save(new RefreshToken(user.getUserId(), refreshToken));
        }

        return JwtTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // 사용자 활성화
    private boolean activateUser(UserAuth user) {
        try {
            user.activate();
            userAuthRepository.save(user);
        } catch (Exception e) {
            log.info(e.getMessage());
            return false;
        }

        return true;
    }

    // 사용자 변경
    public boolean changeUser(UserAuth newUser) {
        userAuthRepository.findById(newUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        try {
            userAuthRepository.save(newUser);
            return true;
        } catch (Exception e) {
            log.info(e.getMessage());
            return false;
        }
    }

    // 비밀번호 변경
    public boolean changePassword(UserAuth user, PasswordChangeRequest request) {
        try {
            log.info("User : " + user);
             userAuthRepository.findById(user.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            request.getOldPassword()
                    )
            );

            user.changePassword(passwordEncoder.encode(request.getNewPassword()));

            userAuthRepository.save(user);
            return true;
        } catch (Exception e) {
            log.info(e.getMessage());
            return false;
        }
    }

    // 사용자 삭제
    public boolean deleteUser(Long userId) {
        try {
            UserAuth user = userAuthRepository.findById(userId).orElseThrow();
            user.deleteUser();
            userAuthRepository.save(user);
            return true;
        } catch (Exception e) {
            log.info(e.getMessage());
            return false;
        }
    }

    @Transactional
    public void updateUserInfo(UserAuth user, UpdateUserRequest request) {
        UserAuth userAuth = userAuthRepository.findById(user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        userAuth.updateName(request.getName());
        userAuthRepository.save(userAuth);
    }

    @Transactional
    public boolean withdraw(UserAuth user, WithdrawRequest request) {
        UserAuth userAuth = userAuthRepository.findById(user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (!passwordEncoder.matches(request.getPassword(), userAuth.getPassword())) {
            return false;
        }
        userAuth.deleteUser();
        userAuthRepository.save(userAuth);
        return true;
    }


    private KakaoTokenResponse requestToken(String accessCode) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON)); // 이 부분 추가

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoKey);
        params.add("redirect_uri", "http://localhost:8088/api/auth/kakao");
        params.add("code", accessCode);
        params.add("client_secret", clientSecretKey);

        HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest = new HttpEntity<>(params, headers);

        ResponseEntity<KakaoTokenResponse> response = restTemplate.exchange(
                "https://kauth.kakao.com/oauth/token",
                HttpMethod.POST,
                kakaoTokenRequest,
                KakaoTokenResponse.class);

        KakaoTokenResponse kakaoToken = null;

        try {
            kakaoToken = response.getBody();
            log.info("kakaoToken : " + kakaoToken.getAccess_token());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return kakaoToken;
    }

    private KakaoProfileResponse requestProfile(KakaoTokenResponse kakaoToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();

        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
        headers.add("Authorization","Bearer "+ kakaoToken.getAccess_token());

        HttpEntity<MultiValueMap<String,String>> kakaoProfileRequest = new HttpEntity <>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.GET,
                kakaoProfileRequest,
                String.class);

        ObjectMapper objectMapper = new ObjectMapper();
        KakaoProfileResponse kakaoProfile = null;
        try {
            kakaoProfile = objectMapper.readValue(response.getBody(), KakaoProfileResponse.class);
            log.info("Kakao Profile : " + kakaoProfile.getKakao_account().getEmail());
        } catch (Exception e) {
            log.info(Arrays.toString(e.getStackTrace()));
            System.out.println(e.getMessage());
        }

        return kakaoProfile;
    }
}
