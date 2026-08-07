package com.cenergy.passed_backend.domain.user.repository;

import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.skill.entity.UserSkill;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class JpaUserSkillProvider implements UserSkillProvider {
    private final UserSkillRepository userSkillRepository;

    public JpaUserSkillProvider(UserSkillRepository userSkillRepository) {
        this.userSkillRepository = userSkillRepository;
    }

    @Override
    public List<UserSkillData> findByUserId(Long userId) {
        return userSkillRepository.findAllByUserIdOrderBySkill_IdAsc(userId).stream()
                .map(this::toUserSkillData)
                .toList();
    }

    private UserSkillData toUserSkillData(UserSkill userSkill) {
        return new UserSkillData(
                userSkill.getSkill().getId(),
                userSkill.getSkillLevel(),
                userSkill.isImportant()
        );
    }
}
