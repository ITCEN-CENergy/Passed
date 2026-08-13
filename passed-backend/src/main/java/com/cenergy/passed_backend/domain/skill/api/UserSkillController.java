package com.cenergy.passed_backend.domain.skill.api;

import com.cenergy.passed_backend.domain.skill.application.UserSkillPreferenceService;
import com.cenergy.passed_backend.domain.skill.application.UserSkillQueryService;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillEvidenceListResponse;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillListResponse;
import com.cenergy.passed_backend.domain.skill.dto.UserSkillPreferenceUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/skills")
public class UserSkillController {
    private final UserSkillQueryService queryService;
    private final UserSkillPreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<UserSkillListResponse> findAll() {
        return ResponseEntity.ok(queryService.findAll());
    }

    @PutMapping("/preferences")
    public ResponseEntity<UserSkillListResponse> updatePreferences(
            @Valid @RequestBody UserSkillPreferenceUpdateRequest request
    ) {
        return ResponseEntity.ok(preferenceService.update(request));
    }

    @GetMapping("/{userSkillId}/evidences")
    public ResponseEntity<UserSkillEvidenceListResponse> findEvidences(
            @PathVariable Long userSkillId
    ) {
        return ResponseEntity.ok(queryService.findEvidences(userSkillId));
    }
}
