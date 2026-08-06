package com.cenergy.passed_backend.domain.user.repository;

import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;

import java.util.List;

public interface UserSkillProvider {
    List<UserSkillData> findByUserId(Long userId);
}
