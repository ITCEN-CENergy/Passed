package com.cenergy.passed_backend.domain.resume.dto;

import com.cenergy.passed_backend.domain.resume.entity.ProficiencyLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 이력서 등록과 전체 수정이 공유하는 스냅샷 요청이다.
 * Q. 선택 항목을 왜 null 대신 빈 배열로 받나요?
 * A. PUT에서 누락과 삭제를 혼동하지 않도록, 현재 전체 상태를 항상 명시하게 하기 위해서다.
 */
public record ResumeUpsertRequest(
        @NotNull @Valid PersonalInfoRequest personalInfo,
        @NotEmpty List<@Valid EducationRequest> educations,
        @NotNull List<@Valid ExperienceRequest> experiences,
        @NotNull List<@Valid ActivityRequest> activities,
        @NotNull List<@Valid TrainingRequest> trainings,
        @NotNull List<@Valid CertificationRequest> certifications,
        @NotNull List<@Valid AwardRequest> awards,
        @NotNull List<@Valid OverseasExperienceRequest> overseasExperiences,
        @NotNull List<@Valid LanguageProficiencyRequest> languageProficiencies
) {
    public record PersonalInfoRequest(
            @NotNull LocalDate birthDate,
            @NotBlank @Size(max = 10) String gender,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 50) String phone,
            @NotBlank @Size(max = 255) String address,
            @Size(max = 500) String photoUrl
    ) {
    }

    public record EducationRequest(
            @Positive Long id,
            @Size(max = 50) String schoolType,
            @NotBlank @Size(max = 100) String schoolName,
            LocalDate admissionDate,
            LocalDate graduationDate,
            @Size(max = 50) String status,
            Boolean isTransfer,
            @Size(max = 100) String majorName,
            @DecimalMin("0.0") BigDecimal gpa,
            @DecimalMin(value = "0.0", inclusive = false) BigDecimal maxGpa,
            @Size(max = 200) String otherMajors
    ) {
    }

    public record ExperienceRequest(
            @Positive Long id,
            @NotBlank @Size(max = 100) String companyName,
            @Size(max = 100) String departmentName,
            LocalDate startDate,
            LocalDate endDate,
            Boolean isWorking,
            @Size(max = 50) String position,
            String responsibilities,
            @Size(max = 50) String salary,
            String careerDescription
    ) {
    }

    public record ActivityRequest(
            @Positive Long id,
            @Size(max = 50) String activityType,
            @Size(max = 100) String organization,
            LocalDate startDate,
            LocalDate endDate,
            String description
    ) {
    }

    public record TrainingRequest(
            @Positive Long id,
            @Size(max = 150) String name,
            @Size(max = 100) String institution,
            LocalDate startDate,
            LocalDate endDate,
            String description
    ) {
    }

    public record CertificationRequest(
            @Positive Long id,
            @Size(max = 150) String name,
            @Size(max = 100) String issuer,
            LocalDate acquisitionDate
    ) {
    }

    public record AwardRequest(
            @Positive Long id,
            @Size(max = 150) String name,
            @Size(max = 100) String issuer,
            LocalDate awardDate,
            String description
    ) {
    }

    public record OverseasExperienceRequest(
            @Positive Long id,
            @Size(max = 100) String countryName,
            LocalDate startDate,
            LocalDate endDate,
            String description
    ) {
    }

    public record LanguageProficiencyRequest(
            @Positive Long id,
            @NotBlank @Size(max = 50) String languageName,
            @NotNull ProficiencyLevel proficiencyLevel
    ) {
    }
}
