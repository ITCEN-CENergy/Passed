package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 공고별 자기소개서의 사용자 소유권을 항상 조회 조건에 포함하는 저장소다.
 * ID만으로 조회하지 않아 다른 사용자의 리소스가 노출되는 것을 막는다.
 */
public interface CoverLetterCompanyRepository extends JpaRepository<CoverLetterCompany, Long> {

    /** 현재 사용자의 자기소개서 목록과 목록에 필요한 공고·기업 정보를 최근 수정 순으로 가져온다. */
    @Query("""
            select coverLetter
            from CoverLetterCompany coverLetter
            join fetch coverLetter.jobPosting jobPosting
            join fetch jobPosting.company
            where coverLetter.user.id = :userId
            order by coverLetter.updatedAt desc, coverLetter.id desc
            """)
    List<CoverLetterCompany> findAllOwnedSummary(@Param("userId") Long userId);

    /** 한 사용자가 동일 공고에 이미 자기소개서를 작성했는지 확인한다. */
    boolean existsByUserIdAndJobPostingId(Long userId, Long jobPostingId);

    /** 자기소개서 상세 응답에 필요한 공고와 기업을 한 쿼리로 로딩한다. */
    @Query("""
            select coverLetter
            from CoverLetterCompany coverLetter
            join fetch coverLetter.jobPosting jobPosting
            join fetch jobPosting.company
            where coverLetter.id = :coverLetterId
              and coverLetter.user.id = :userId
            """)
    Optional<CoverLetterCompany> findOwnedDetail(
            @Param("coverLetterId") Long coverLetterId,
            @Param("userId") Long userId
    );

    /** 수정 또는 삭제 전에 부모 자기소개서를 잠가 순서 변경과 삭제의 경합을 줄인다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select coverLetter
            from CoverLetterCompany coverLetter
            where coverLetter.id = :coverLetterId
              and coverLetter.user.id = :userId
            """)
    Optional<CoverLetterCompany> findOwnedForUpdate(
            @Param("coverLetterId") Long coverLetterId,
            @Param("userId") Long userId
    );
}
