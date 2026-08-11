package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.auth.entity.CustomUserDetails;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 인증된 principal에서 현재 사용자 ID를 제공해 도메인 서비스의 소유권 검사를 연결한다.
 */
@Component
@Profile("!fixed-dev-user")
public class SecurityCurrentUserIdProvider implements CurrentUserIdProvider {

    @Override
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            throw new InsufficientAuthenticationException("인증된 사용자가 필요합니다.");
        }
        return principal.getUserId();
    }
}
