package com.cenergy.passed_backend.domain.jobposting.repository;

import com.cenergy.passed_backend.domain.jobposting.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
