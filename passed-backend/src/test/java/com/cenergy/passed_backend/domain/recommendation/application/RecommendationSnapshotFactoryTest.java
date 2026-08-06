package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationSnapshotFactoryTest {
    private final RecommendationSnapshotFactory factory =
            new RecommendationSnapshotFactory(new ObjectMapper());

    @Test
    void createsStableCanonicalHashForTheSameSortedSkills() {
        List<UserSkillData> skills = List.of(
                new UserSkillData(12L, (short) 3, true),
                new UserSkillData(16L, (short) 1, false)
        );

        var first = factory.create(skills, 8L, List.of());
        var second = factory.create(skills, 8L, List.of());

        assertEquals(first.userSkillSnapshotHash(), second.userSkillSnapshotHash());
        assertEquals(
                "a9510a28fd8e8bf2f7cd3db50a27abffa0fed74b308989fb8af8863734965421",
                first.userSkillSnapshotHash()
        );
        assertTrue(first.userSkillSnapshotHash().matches("^[0-9a-f]{64}$"));
        assertEquals(1, first.userSkillSnapshot().get("schemaVersion"));
    }

    @Test
    void keepsEmptyJobRoleConditionWithoutLookingUpAnIndustryName() {
        var result = factory.create(
                List.of(new UserSkillData(12L, (short) 3, true)),
                8L,
                List.of()
        );

        assertEquals(8L, result.preferenceSnapshot().get("industryId"));
        assertNull(result.preferenceSnapshot().get("industryName"));
        assertEquals(List.of(), result.preferenceSnapshot().get("jobRoleIds"));
        assertEquals(List.of(), result.preferenceSnapshot().get("jobRoles"));
    }
}
