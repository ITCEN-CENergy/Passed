package com.cenergy.passed_backend.domain.user.entity;

import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
@Setter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "field", length = 100)
    private String field;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desired_jobs", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> desiredJobs = new ArrayList<>();

}
