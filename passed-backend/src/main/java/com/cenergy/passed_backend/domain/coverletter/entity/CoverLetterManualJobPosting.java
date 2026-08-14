package com.cenergy.passed_backend.domain.coverletter.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/** 사용자가 자기소개서 목록에서 직접 입력한 채용공고 스냅샷이다. */
@Entity
@Getter
@Table(name = "cover_letter_manual_job_postings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterManualJobPosting extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "posting_title", nullable = false, length = 255)
    private String postingTitle;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "job_role_name", nullable = false, length = 255)
    private String jobRoleName;

    @Column(name = "position_detail", columnDefinition = "text")
    private String positionDetail;

    @Column(name = "career_type", length = 50)
    private String careerType;

    @Column(name = "hire_type", length = 255)
    private String hireType;

    @Column(name = "main_duty", columnDefinition = "text")
    private String mainDuty;

    @Column(name = "qualification", columnDefinition = "text")
    private String qualification;

    @Column(name = "preference", columnDefinition = "text")
    private String preference;

    public static CoverLetterManualJobPosting create(
            String postingTitle,
            String companyName,
            String jobRoleName,
            String positionDetail,
            String careerType,
            String hireType,
            String mainDuty,
            String qualification,
            String preference
    ) {
        CoverLetterManualJobPosting value = new CoverLetterManualJobPosting();
        value.update(postingTitle, companyName, jobRoleName, positionDetail, careerType,
                hireType, mainDuty, qualification, preference);
        return value;
    }

    /** 새 값이 기존 스냅샷과 다르면 갱신하고 true를 반환한다. */
    public boolean update(
            String postingTitle,
            String companyName,
            String jobRoleName,
            String positionDetail,
            String careerType,
            String hireType,
            String mainDuty,
            String qualification,
            String preference
    ) {
        String normalizedPostingTitle = requireText(postingTitle, "postingTitle");
        String normalizedCompanyName = normalize(companyName);
        String normalizedJobRoleName = requireText(jobRoleName, "jobRoleName");
        boolean changed = !Objects.equals(this.postingTitle, normalizedPostingTitle)
                || !Objects.equals(this.companyName, normalizedCompanyName)
                || !Objects.equals(this.jobRoleName, normalizedJobRoleName)
                || !Objects.equals(this.positionDetail, normalize(positionDetail))
                || !Objects.equals(this.careerType, normalize(careerType))
                || !Objects.equals(this.hireType, normalize(hireType))
                || !Objects.equals(this.mainDuty, normalize(mainDuty))
                || !Objects.equals(this.qualification, normalize(qualification))
                || !Objects.equals(this.preference, normalize(preference));
        this.postingTitle = normalizedPostingTitle;
        this.companyName = normalizedCompanyName;
        this.jobRoleName = normalizedJobRoleName;
        this.positionDetail = normalize(positionDetail);
        this.careerType = normalize(careerType);
        this.hireType = normalize(hireType);
        this.mainDuty = normalize(mainDuty);
        this.qualification = normalize(qualification);
        this.preference = normalize(preference);
        return changed;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
