package com.cenergy.passed_backend.domain.resume.dto;

import com.cenergy.passed_backend.domain.resume.entity.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 프론트 편집 화면이 등록 요청과 같은 구조로 다시 채울 수 있는 이력서 응답이다. */
public record ResumeResponse(
        Long resumeId,
        PersonalInfoResponse personalInfo,
        List<EducationResponse> educations,
        List<ExperienceResponse> experiences,
        List<ActivityResponse> activities,
        List<TrainingResponse> trainings,
        List<CertificationResponse> certifications,
        List<AwardResponse> awards,
        List<OverseasExperienceResponse> overseasExperiences,
        List<LanguageProficiencyResponse> languageProficiencies,
        OffsetDateTime createdAt
) {
    public record PersonalInfoResponse(Long id, LocalDate birthDate, String gender, String email,
                                       String phone, String address, String photoUrl) {
        public static PersonalInfoResponse from(PersonalInfo value) {
            return new PersonalInfoResponse(value.getId(), value.getBirthDate(), value.getGender(),
                    value.getEmail(), value.getPhone(), value.getAddress(), value.getPhotoUrl());
        }
    }

    public record EducationResponse(Long id, String schoolType, String schoolName,
                                    LocalDate admissionDate, LocalDate graduationDate, String status,
                                    Boolean isTransfer, String majorName, BigDecimal gpa,
                                    BigDecimal maxGpa, String otherMajors) {
        public static EducationResponse from(Education value) {
            return new EducationResponse(value.getId(), value.getSchoolType(), value.getSchoolName(),
                    value.getAdmissionDate(), value.getGraduationDate(), value.getStatus(),
                    value.getTransfer(), value.getMajorName(), value.getGpa(), value.getMaxGpa(),
                    value.getOtherMajors());
        }
    }

    public record ExperienceResponse(Long id, String companyName, String departmentName,
                                     LocalDate startDate, LocalDate endDate, Boolean isWorking,
                                     String position, String responsibilities, String salary,
                                     String careerDescription) {
        public static ExperienceResponse from(Experience value) {
            return new ExperienceResponse(value.getId(), value.getCompanyName(), value.getDepartmentName(),
                    value.getStartDate(), value.getEndDate(), value.getWorking(), value.getPosition(),
                    value.getResponsibilities(), value.getSalary(), value.getCareerDescription());
        }
    }

    public record ActivityResponse(Long id, String activityType, String organization,
                                   LocalDate startDate, LocalDate endDate, String description) {
        public static ActivityResponse from(Activity value) {
            return new ActivityResponse(value.getId(), value.getActivityType(), value.getOrganization(),
                    value.getStartDate(), value.getEndDate(), value.getDescription());
        }
    }

    public record TrainingResponse(Long id, String name, String institution,
                                   LocalDate startDate, LocalDate endDate, String description) {
        public static TrainingResponse from(Training value) {
            return new TrainingResponse(value.getId(), value.getName(), value.getInstitution(),
                    value.getStartDate(), value.getEndDate(), value.getDescription());
        }
    }

    public record CertificationResponse(Long id, String name, String issuer, LocalDate acquisitionDate) {
        public static CertificationResponse from(Certification value) {
            return new CertificationResponse(value.getId(), value.getName(), value.getIssuer(),
                    value.getAcquisitionDate());
        }
    }

    public record AwardResponse(Long id, String name, String issuer,
                                LocalDate awardDate, String description) {
        public static AwardResponse from(Award value) {
            return new AwardResponse(value.getId(), value.getName(), value.getIssuer(),
                    value.getAwardDate(), value.getDescription());
        }
    }

    public record OverseasExperienceResponse(Long id, String countryName,
                                              LocalDate startDate, LocalDate endDate, String description) {
        public static OverseasExperienceResponse from(OverseasExperience value) {
            return new OverseasExperienceResponse(value.getId(), value.getCountryName(),
                    value.getStartDate(), value.getEndDate(), value.getDescription());
        }
    }

    public record LanguageProficiencyResponse(Long id, String languageName,
                                              ProficiencyLevel proficiencyLevel) {
        public static LanguageProficiencyResponse from(LanguageProficiency value) {
            return new LanguageProficiencyResponse(value.getId(), value.getLanguageName(),
                    value.getProficiencyLevel());
        }
    }
}
