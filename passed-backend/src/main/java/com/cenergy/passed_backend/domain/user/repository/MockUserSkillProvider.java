package com.cenergy.passed_backend.domain.user.repository;

import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.user.repository.UserSkillProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MockUserSkillProvider implements UserSkillProvider {
    private static final Map<Long, List<UserSkillData>> USER_SKILLS = Map.of(
            2L,
            List.of(
                    skill(1498, 1, false),
                    skill(1409, 1, true),
                    skill(1589, 1, false),
                    skill(1355, 1, false),
                    skill(1344, 3, true),
                    skill(1339, 2, false),
                    skill(1328, 3, false),
                    skill(548, 3, true),
                    skill(1225, 2, false),
                    skill(1146, 3, false),
                    skill(843, 3, false),
                    skill(16, 1, false),
                    skill(13, 2, false),
                    skill(107, 3, true),
                    skill(12, 3, true)
            )
    );

    @Override
    public List<UserSkillData> findByUserId(Long userId) {
        return USER_SKILLS.getOrDefault(userId, List.of());
    }

    private static UserSkillData skill(long skillId, int skillLevel, boolean important) {
        return new UserSkillData(skillId, (short) skillLevel, important);
    }
}
