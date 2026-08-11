package com.cenergy.passed_backend.domain.resume.repository;

import com.cenergy.passed_backend.domain.resume.entity.Resume;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    Optional<Resume> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select resume from Resume resume where resume.user.id = :userId")
    Optional<Resume> findByUserIdForUpdate(@Param("userId") Long userId);
}
