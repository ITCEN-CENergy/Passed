package com.cenergy.passed_backend.domain.coverletter.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 인증 사용자가 특정 채용공고를 위해 작성하는 자기소개서의 루트 엔티티다.
 * 데이터베이스의 (user_id, job_posting_id) 유일 제약으로 사용자당 공고별 한 건만 유지한다.
 */
@Entity
@Getter
@Table(
        name = "cover_letters_company",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cover_letters_company_user_job_posting",
                columnNames = {"user_id", "job_posting_id"}
        ),
        indexes = @Index(
                name = "idx_cover_letters_company_job_posting_id",
                columnList = "job_posting_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterCompany extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;

    @OneToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "manual_job_posting_id", unique = true)
    private CoverLetterManualJobPosting manualJobPosting;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    /**
     * 새 공고별 자기소개서를 생성한다.
     * 제목은 공백만 저장되지 않도록 정규화해 DB CHECK 제약과 같은 규칙을 적용한다.
     */
    public static CoverLetterCompany create(User user, JobPosting jobPosting, String title) {
        CoverLetterCompany value = new CoverLetterCompany();
        value.user = user;
        value.jobPosting = jobPosting;
        value.updateTitle(title);
        return value;
    }

    /** 자기소개서 목록에서 직접 입력한 공고와 자기소개서를 함께 생성한다. */
    public static CoverLetterCompany createManual(
            User user,
            CoverLetterManualJobPosting manualJobPosting,
            String title
    ) {
        CoverLetterCompany value = new CoverLetterCompany();
        value.user = Objects.requireNonNull(user, "user must not be null");
        value.manualJobPosting = Objects.requireNonNull(manualJobPosting, "manualJobPosting must not be null");
        value.updateTitle(title);
        return value;
    }

    public boolean isManual() {
        return manualJobPosting != null;
    }

    /** 직접 입력형 자기소개서의 공고 스냅샷만 수정할 수 있다. */
    public boolean updateManualJobPosting(
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
        if (!isManual()) {
            throw new IllegalStateException("linked cover letter cannot update manual job posting");
        }
        return manualJobPosting.update(postingTitle, companyName, jobRoleName, positionDetail,
                careerType, hireType, mainDuty, qualification, preference);
    }

    /**
     * 자기소개서 제목을 변경한다.
     * 공백 제목은 유효한 도메인 값이 아니므로 즉시 거부한다.
     */
    public void updateTitle(String title) {
        this.title = requireText(title, "title");
    }

    /** 문항 추가·수정·삭제를 자기소개서 전체의 최종 수정 시각에 반영한다. */
    public void markItemsChanged() {
        touchUpdatedAt();
    }

    /**
     * 사용자 입력 텍스트를 trim하고, 비어 있으면 호출자에게 잘못된 입력임을 알린다.
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
