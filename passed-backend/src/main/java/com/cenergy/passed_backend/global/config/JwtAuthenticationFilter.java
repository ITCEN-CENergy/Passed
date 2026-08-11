package com.cenergy.passed_backend.global.config;

import com.cenergy.passed_backend.domain.auth.entity.CustomUserDetails;
import com.cenergy.passed_backend.domain.auth.application.CustomUserDetailService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ACCESS_COOKIE = "accessToken";

    private final JwtProvider jwtProvider;
    private final CustomUserDetailService customUserDetailService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveAccessToken(request);

        if (StringUtils.hasText(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request, token);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            if (!jwtProvider.isAccessToken(token)) {
                return;
            }
            Long userId = jwtProvider.extractUserId(token);
            CustomUserDetails principal = customUserDetailService.loadById(userId);
            if (!jwtProvider.isValidAccessToken(token, principal)) {
                return;
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ignored) {
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveAccessToken(HttpServletRequest request) {
        return cookieValue(request, ACCESS_COOKIE);
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
