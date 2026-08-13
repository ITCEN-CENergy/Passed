package com.cenergy.passed_backend.global.security;

/**
 * 현재 요청에 인증된 사용자 ID를 제공한다.
 */
public interface CurrentUserIdProvider {
    Long getCurrentUserId();
}
