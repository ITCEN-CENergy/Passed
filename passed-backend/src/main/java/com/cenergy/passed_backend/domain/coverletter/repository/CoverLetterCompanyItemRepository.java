package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 공고별 자기소개서 문항 저장소다.
 * 모든 단건 조회는 부모의 사용자 ID를 함께 검사해 소유권을 보장한다.
 */
public interface CoverLetterCompanyItemRepository extends JpaRepository<CoverLetterCompanyItem, Long> {

    /** 부모 자기소개서의 문항을 화면 표시 순서대로 반환한다. */
    List<CoverLetterCompanyItem> findAllByCoverLetterCompanyIdOrderByDisplayOrderAscIdAsc(Long coverLetterCompanyId);

    /** 새 문항의 displayOrder가 이미 사용 중인지 확인한다. */
    boolean existsByCoverLetterCompanyIdAndDisplayOrder(Long coverLetterCompanyId, int displayOrder);

    /** 수정 대상 자신을 제외하고 displayOrder 중복 여부를 확인한다. */
    boolean existsByCoverLetterCompanyIdAndDisplayOrderAndIdNot(
            Long coverLetterCompanyId,
            int displayOrder,
            Long itemId
    );

    @Query("""
            select item
            from CoverLetterCompanyItem item
            join fetch item.coverLetterCompany coverLetter
            left join fetch coverLetter.jobPosting jobPosting
            left join fetch jobPosting.company
            left join fetch jobPosting.jobRole
            left join fetch coverLetter.manualJobPosting
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
