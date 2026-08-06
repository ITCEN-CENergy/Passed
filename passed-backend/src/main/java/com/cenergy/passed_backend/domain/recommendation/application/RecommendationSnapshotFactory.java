package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RecommendationSnapshotFactory {
    private static final int SNAPSHOT_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public RecommendationSnapshotFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SnapshotResult create(
            List<UserSkillData> sortedUserSkills,
            Long industryId,
            List<JobRoleSnapshot> sortedJobRoles
    ) {
        Map<String, Object> userSkillSnapshot = userSkillSnapshot(sortedUserSkills);
        Map<String, Object> preferenceSnapshot = preferenceSnapshot(industryId, sortedJobRoles);
        return new SnapshotResult(
                userSkillSnapshot,
                sha256(userSkillSnapshot),
                preferenceSnapshot
        );
    }

    private Map<String, Object> userSkillSnapshot(List<UserSkillData> sortedUserSkills) {
        List<Map<String, Object>> skills = sortedUserSkills.stream()
                .map(skill -> linkedMap(
                        "skillId", skill.skillId(),
                        "skillLevel", skill.skillLevel(),
                        "isImportant", skill.important()
                ))
                .toList();
        return linkedMap("schemaVersion", SNAPSHOT_SCHEMA_VERSION, "skills", skills);
    }

    private Map<String, Object> preferenceSnapshot(
            Long industryId,
            List<JobRoleSnapshot> sortedJobRoles
    ) {
        String industryName = sortedJobRoles.isEmpty() ? null : sortedJobRoles.getFirst().industryName();
        List<Long> jobRoleIds = sortedJobRoles.stream().map(JobRoleSnapshot::jobRoleId).toList();
        List<Map<String, Object>> jobRoles = sortedJobRoles.stream()
                .map(role -> linkedMap(
                        "jobRoleId", role.jobRoleId(),
                        "jobRoleName", role.jobRoleName()
                ))
                .toList();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", SNAPSHOT_SCHEMA_VERSION);
        snapshot.put("industryId", industryId);
        snapshot.put("industryName", industryName);
        snapshot.put("jobRoleIds", jobRoleIds);
        snapshot.put("jobRoles", jobRoles);
        return snapshot;
    }

    private String sha256(Map<String, Object> snapshot) {
        try {
            byte[] canonicalJson = objectMapper.writeValueAsBytes(snapshot);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalJson));
        } catch (JacksonException | NoSuchAlgorithmException exception) {
            throw new RecommendationException(
                    ErrorCode.RECOMMENDATION_SKILL_DATA_INVALID,
                    "Failed to create user skill snapshot"
            );
        }
    }

    private Map<String, Object> linkedMap(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
    }

    public record JobRoleSnapshot(
            Long jobRoleId,
            String jobRoleName,
            Long industryId,
            String industryName
    ) {
    }

    public record SnapshotResult(
            Map<String, Object> userSkillSnapshot,
            String userSkillSnapshotHash,
            Map<String, Object> preferenceSnapshot
    ) {
    }
}
