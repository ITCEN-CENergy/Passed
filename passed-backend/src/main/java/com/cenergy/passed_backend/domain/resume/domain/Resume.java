package com.cenergy.passed_backend.resume.entity;

import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import com.cenergy.passed_backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "resumes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resume extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
}
