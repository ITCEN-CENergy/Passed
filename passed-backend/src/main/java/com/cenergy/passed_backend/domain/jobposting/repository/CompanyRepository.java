package com.cenergy.passed_backend.domain.jobposting.repository;

import com.cenergy.passed_backend.domain.jobposting.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    @Query("select company.id as id, company.companyName as name from Company company order by company.companyName asc")
    List<CompanyNameView> findAllNames();

    interface CompanyNameView {
        Long getId();
        String getName();
    }
}
