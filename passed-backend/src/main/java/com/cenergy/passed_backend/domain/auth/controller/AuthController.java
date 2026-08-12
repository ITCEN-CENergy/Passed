package com.cenergy.passed_backend.domain.auth.controller;

import com.cenergy.passed_backend.domain.auth.dto.request.LoginRequest;
import com.cenergy.passed_backend.domain.auth.dto.request.PasswordChangeRequest;
import com.cenergy.passed_backend.domain.auth.dto.request.RegisterRequest;
import com.cenergy.passed_backend.domain.auth.dto.request.UpdateUserRequest;
import com.cenergy.passed_backend.domain.auth.dto.request.WithdrawRequest;
import com.cenergy.passed_backend.domain.auth.dto.response.JwtTokenResponse;
import com.cenergy.passed_backend.domain.auth.dto.response.LoginResponse;
import com.cenergy.passed_backend.domain.auth.dto.response.UserResponse;
import com.cenergy.passed_backend.domain.auth.entity.CustomUserDetails;
import com.cenergy.passed_backend.domain.auth.application.AuthService;
import com.cenergy.passed_backend.global.config.JwtProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String ACCESS_COOKIE = "accessToken";
    private static final String REFRESH_COOKIE = "refreshToken";

    private final AuthService authService;
    private final JwtProvider jwtProvider;

    @Value("${application.security.cookie.secure:false}")
    private boolean secureCookie;

    @Value("${application.security.cookie.same-site:Lax}")
    private String sameSite;

    @PostMapping("/signup")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @GetMapping("/check-email")
    public ResponseEntity<Boolean> checkEmail(@RequestParam("email") String email) {
        return ResponseEntity.ok(authService.isEmailDuplicated(email));
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        JwtTokenResponse tokens = authService.login(request);
        setAuthenticationCookies(response, tokens);
        return ResponseEntity.ok(new LoginResponse("로그인되었습니다."));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CustomUserDetails principal) {
        return UserResponse.builder()
                .userId(principal.getUserId())
                .email(principal.getEmail())
                .name(principal.getName())
                .role("USER")
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JwtTokenResponse tokens = authService.refresh(refreshToken);
        setAuthenticationCookies(response, tokens);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken);
        clearAuthenticationCookies(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/change")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody PasswordChangeRequest request,
            HttpServletResponse response
    ) {
        authService.changePassword(principal, request);
        clearAuthenticationCookies(response);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me")
    public ResponseEntity<Void> updateUserInfo(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        authService.updateUserInfo(principal, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody WithdrawRequest request,
            HttpServletResponse response
    ) {
        authService.withdraw(principal, request);
        clearAuthenticationCookies(response);
        return ResponseEntity.noContent().build();
    }

    private void setAuthenticationCookies(HttpServletResponse response, JwtTokenResponse tokens) {
        addCookie(response, ACCESS_COOKIE, tokens.getAccessToken(), "/", jwtProvider.accessTokenDuration());
        addCookie(response, REFRESH_COOKIE, tokens.getRefreshToken(), "/api/auth", jwtProvider.refreshTokenDuration());
    }

    private void clearAuthenticationCookies(HttpServletResponse response) {
        addCookie(response, ACCESS_COOKIE, "", "/", Duration.ZERO);
        addCookie(response, REFRESH_COOKIE, "", "/api/auth", Duration.ZERO);
    }

    private void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            String path,
            Duration maxAge
    ) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
