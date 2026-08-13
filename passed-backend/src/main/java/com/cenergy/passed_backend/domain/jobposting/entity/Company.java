package com.cenergy.passed_backend.domain.jobposting.entity;

import com.cenergy.passed_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "companies",
        indexes = {
                @Index(name = "idx_company_name", columnList = "company_name"),
                @Index(name = "idx_company_size", columnList = "company_size")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", length = 200, nullable = false, unique = true)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "company_size", nullable = false, length = 32)
    private CompanySize companySize;

    @Column(name = "talent_profile", columnDefinition = "text")
    private String talentProfile;

    @Column(name = "benefits", columnDefinition = "text")
    private String benefits;

    @Column(name = "region", length = 100, nullable = false)
    private String region;
}
