package com.cenergy.passed_backend.domain.user.api;

import com.cenergy.passed_backend.domain.user.application.UserJobPreferenceService;
import com.cenergy.passed_backend.domain.user.dto.IndustryListResponse;
import com.cenergy.passed_backend.domain.user.dto.JobRoleListResponse;
import com.cenergy.passed_backend.domain.user.dto.UserJobPreferenceResponse;
import com.cenergy.passed_backend.domain.user.dto.UserJobPreferenceUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/preferences")
public class UserJobPreferenceController {
    private final UserJobPreferenceService service;

    public UserJobPreferenceController(UserJobPreferenceService service) {
        this.service = service;
    }

    @PostMapping("/jobs")
    public ResponseEntity<UserJobPreferenceResponse> update(
            @Valid @RequestBody UserJobPreferenceUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(request));
    }

    @GetMapping("/jobs")
    public ResponseEntity<UserJobPreferenceResponse> findCurrent() {
        UserJobPreferenceResponse preference = service.findCurrent();
        return preference == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(preference);
    }

    @GetMapping("/industries")
    public ResponseEntity<IndustryListResponse> findIndustries() {
        return ResponseEntity.ok(service.findIndustries());
    }

    @GetMapping("/industries/{industryId}/job-roles")
    public ResponseEntity<JobRoleListResponse> findJobRoles(@PathVariable Long industryId) {
        return ResponseEntity.ok(service.findJobRoles(industryId));
    }
}
