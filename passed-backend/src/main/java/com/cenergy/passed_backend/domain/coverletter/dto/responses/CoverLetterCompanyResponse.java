package com.cenergy.passed_backend.domain.coverletter.dto.responses;

import com.cenergy.passed_backend.domain.jobposting.domain.JobPosting;
import com.cenergy.passed_backend.domain.user.domain.User;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
public class CoverLetterCompanyResponse {
    private Long id;
    private User user;
    private JobPosting jobPosting;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
