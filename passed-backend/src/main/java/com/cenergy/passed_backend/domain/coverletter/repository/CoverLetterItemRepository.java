package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverLetterItemRepository extends JpaRepository<CoverLetterItem, Long> {
}