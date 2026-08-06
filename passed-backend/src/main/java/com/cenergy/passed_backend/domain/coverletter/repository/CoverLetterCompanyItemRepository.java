package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CoverLetterCompanyItemRepository extends JpaRepository<CoverLetterCompanyItem, Long> {

    @Query("""
            select item
            from CoverLetterCompanyItem item
            join fetch item.coverLetterCompany coverLetter
            join fetch coverLetter.jobPosting
            where item.id = :itemId
              and coverLetter.user.id = :userId
            """)
    Optional<CoverLetterCompanyItem> findOwnedItem(
            @Param("itemId") Long itemId,
            @Param("userId") Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item
            from CoverLetterCompanyItem item
            join item.coverLetterCompany coverLetter
            where item.id = :itemId
              and coverLetter.user.id = :userId
            """)
    Optional<CoverLetterCompanyItem> findOwnedItemForUpdate(
            @Param("itemId") Long itemId,
            @Param("userId") Long userId
    );
}
