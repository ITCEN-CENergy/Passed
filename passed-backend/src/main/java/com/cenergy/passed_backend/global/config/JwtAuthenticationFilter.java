package com.gooroomees.neulbomgil_backend.global.config;

import com.gooroomees.neulbomgil_backend.domain.auth.entity.RefreshToken;
import com.gooroomees.neulbomgil_backend.domain.auth.entity.UserAuth;
import com.gooroomees.neulbomgil_backend.domain.auth.repository.RefreshTokenRepository;
import com.gooroomees.neulbomgil_backend.domain.auth.repository.UserAuthRepository;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 모든 HTTP 요청에서 JWT를 검사하는 필터, OncePerRequestFilter를 상속해 요청 당 한 번만 실행됨
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAuthRepository userAuthRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String accessToken = getCookieValue(request, "accessToken");
        String refreshToken = getCookieValue(request, "refreshToken");

        String token = extractToken(request);
        String userEmail;

        try {
            // 액세스 토큰이 존재하고 인증 정보가 없는 경우만 로직 수행
            if (accessToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 토큰이 만료되었다면 이 시점에서 자동으로 ExpiredJwtException이 발생하여 catch 블록으로 이동함
                userEmail = jwtProvider.extractUsername(accessToken);
                UserAuth user = (UserAuth) this.userDetailsService.loadUserByUsername(userEmail);

                if (jwtProvider.isTokenValid(accessToken, user)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            user.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
                // 토큰이 유효하지 않을 경우(만료가 아닌 서명 오류 등)는 권한이 부여되지 않은 상태로 다음 필터로 진행됨
            }
        } catch (ExpiredJwtException e) {
            // 액세스 토큰이 만료되어 파싱 과정에서 예외가 발생한 경우 리프레쉬 토큰 검증 로직 실행
            if (refreshToken != null) {
                try {
                    if (jwtProvider.isTokenValid(refreshToken)) {
                        // DB에 저장된 리프레쉬 토큰과 비교
                        RefreshToken dbToken = refreshTokenRepository.findByRefreshToken(refreshToken).orElse(null);

                        if (dbToken != null) {
                            Long loginId = dbToken.getUserId();
                            UserAuth user = userAuthRepository.findById(loginId).orElseThrow();

                            // 새 액세스 토큰 발급 및 쿠키 갱신
                            String newAccessToken = jwtProvider.generateAccessToken(user);
                            Cookie cookie = new Cookie("accessToken", newAccessToken);
                            cookie.setHttpOnly(true);
                            cookie.setPath("/");
                            cookie.setMaxAge(60 * 30); // 30분
                            response.addCookie(cookie);

                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    user.getAuthorities()
                            );
                            authToken.setDetails(
                                    new WebAuthenticationDetailsSource().buildDetails(request)
                            );
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        }
                    }
                } catch (ExpiredJwtException ex) {
                    // 리프레쉬 토큰까지 만료된 경우 아무 처리도 하지 않음
                    // Security Context에 인증 정보가 없으므로 Security 설정에 의해 접근 차단됨
                } catch (Exception ex) {
                    // 기타 리프레시 토큰 검증 중 발생한 예외 처리
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private String getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}