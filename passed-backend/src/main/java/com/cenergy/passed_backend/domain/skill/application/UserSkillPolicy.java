package com.cenergy.passed_backend.domain.skill.application;

public final class UserSkillPolicy {
    public static final int MINIMUM_ANALYZED_SKILL_COUNT = 4;

    private UserSkillPolicy() {
    }

    public static int maxImportantCount(int totalSkillCount) {
        if (totalSkillCount <= 0) {
            return 0;
        }
        int thirtyPercentCeiling = (totalSkillCount * 3 + 9) / 10;
        return Math.min(totalSkillCount, Math.max(3, thirtyPercentCeiling));
    }
}
