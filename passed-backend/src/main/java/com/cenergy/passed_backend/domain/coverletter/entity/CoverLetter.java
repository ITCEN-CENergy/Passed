package com.cenergy.passed_backend.domain.coverletter.entity;

import com.cenergy.passed_backend.common.entity.CreatedAtEntity;
import com.cenergy.passed_backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자별 기본(공통) 자기소개서를 나타내는 엔티티다.
 * 사용자당 1개의 공통 자기소개서를 가질 수 있도록 (user_id) 유일 제약을 갖는다.
 */
@Getter
@Entity
@Table(name = "cover_letters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetter extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    //조윤지: 사용자별 공통 자기소개서 생성
    public static CoverLetter create(User user) {
        CoverLetter coverLetter = new CoverLetter();
        coverLetter.user = user;
        return coverLetter;
    }

}
