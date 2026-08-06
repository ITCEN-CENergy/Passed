package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverLetterCompanyRepository extends JpaRepository<CoverLetterCompany, Long> {
}
