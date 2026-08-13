package com.cenergy.passed_backend.domain.jobposting.api;

import com.cenergy.passed_backend.domain.jobposting.application.JobPostingCommandService;
import com.cenergy.passed_backend.domain.jobposting.application.JobPostingQueryService;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingCreateRequest;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingCreateResponse;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingDetailResponse;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingListRequest;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingListResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jobPostings")
public class JobPostingController {
    private final JobPostingQueryService queryService;
    private final JobPostingCommandService commandService;

    public JobPostingController(
            JobPostingQueryService queryService,
            JobPostingCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping
    public ResponseEntity<JobPostingListResponse> getJobPostings(
            @Valid @ModelAttribute JobPostingListRequest jobPostingListRequest
    ) {
        return ResponseEntity.ok(queryService.findAll(jobPostingListRequest));
    }

    @GetMapping("/{jobPostingId}")
    public ResponseEntity<JobPostingDetailResponse> getJobPosting(
            @PathVariable Long jobPostingId
    ) {
        return ResponseEntity.ok(queryService.findById(jobPostingId));
    }

    @PostMapping
    public ResponseEntity<JobPostingCreateResponse> createJobPosting(
            @Valid @RequestBody JobPostingCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commandService.create(request));
    }
}
