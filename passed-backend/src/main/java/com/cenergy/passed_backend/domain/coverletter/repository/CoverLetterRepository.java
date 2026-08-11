package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetter;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
//조윤지: 사용자별 공통 자기소개서 조회, 생성, 수정, 삭제를 위한 Repository
public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {
    Optional<CoverLetter> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select coverLetter from CoverLetter coverLetter where coverLetter.user.id = :userId")
    Optional<CoverLetter> findByUserIdForUpdate(@Param("userId") Long userId);
}
