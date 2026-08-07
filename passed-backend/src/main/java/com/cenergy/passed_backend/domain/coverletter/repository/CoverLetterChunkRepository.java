package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterChunk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverLetterChunkRepository extends JpaRepository<CoverLetterChunk, Long> {
}