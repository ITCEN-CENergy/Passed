package com.cenergy.passed_backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PasswordChangeRequest {
    @NotBlank
    private String oldPassword;

    @NotBlank
    @Size(min = 8, max = 72)
    private String newPassword;
}
