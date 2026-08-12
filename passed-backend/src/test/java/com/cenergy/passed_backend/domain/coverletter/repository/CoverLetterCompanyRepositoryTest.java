package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 자기소개서 목록의 소유권, 정렬, 최종 수정일 저장 규칙을 실제 JPA 쿼리로 검증한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CoverLetterCompanyRepositoryTest {
    @Autowired
    CoverLetterCompanyRepository coverLetterRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void returnsOnlyOwnedCoverLettersInLatestUpdatedOrder() {
        long userId = insertUser("owner");
        long otherUserId = insertUser("other");
        long olderId = insertCoverLetter(userId, insertJobPosting("older"), "이전 자기소개서", "2026-08-10T10:00:00+09:00");
        long latestId = insertCoverLetter(userId, insertJobPosting("latest"), "최근 자기소개서", "2026-08-12T10:00:00+09:00");
        insertCoverLetter(otherUserId, insertJobPosting("hidden"), "다른 사용자 자기소개서", "2026-08-13T10:00:00+09:00");

        var result = coverLetterRepository.findAllOwnedSummary(userId);

        assertThat(result)
                .extracting(CoverLetterCompany::getId)
                .containsExactly(latestId, olderId);
        assertThat(result)
                .extracting(CoverLetterCompany::getTitle)
                .containsExactly("최근 자기소개서", "이전 자기소개서");
    }

    @Test
    void updatesParentTimestampWhenItemsChange() {
        long userId = insertUser("modified");
        long coverLetterId = insertCoverLetter(
                userId,
                insertJobPosting("modified"),
                "수정할 자기소개서",
                "2020-01-01T00:00:00+09:00"
        );
        CoverLetterCompany coverLetter = coverLetterRepository
                .findOwnedDetail(coverLetterId, userId)
                .orElseThrow();

        coverLetter.markItemsChanged();
        coverLetterRepository.flush();

        OffsetDateTime updatedAt = jdbc.queryForObject(
                "select updated_at from cover_letters_company where id = ?",
                OffsetDateTime.class,
                coverLetterId
        );
        assertThat(updatedAt).isAfter(OffsetDateTime.parse("2020-01-01T00:00:00+09:00"));
    }

    private long insertUser(String label) {
        String unique = Long.toUnsignedString(System.nanoTime());
        return id(
                "insert into users(name, email, password) values (?, ?, 'pw') returning id",
                label,
                label + "-" + unique + "@cover-letter-list.test"
        );
    }

    private long insertJobPosting(String label) {
        String unique = Long.toUnsignedString(System.nanoTime());
        long industryId = id(
                "insert into industries(industry_name) values (?) returning id",
                "list-industry-" + label + "-" + unique
        );
        long roleId = id(
                "insert into job_roles(industry_id, job_role_name) values (?, ?) returning id",
                industryId,
                "list-role-" + label + "-" + unique
        );
        long companyId = id(
                "insert into companies(company_name) values (?) returning id",
                "list-company-" + label + "-" + unique
        );
        return id(
                "insert into job_postings(title, company_id, job_role_id) values (?, ?, ?) returning id",
                "list-posting-" + label,
                companyId,
                roleId
        );
    }

    private long insertCoverLetter(long userId, long jobPostingId, String title, String updatedAt) {
        return id("""
                insert into cover_letters_company(user_id, job_posting_id, title, updated_at)
                values (?, ?, ?, cast(? as timestamptz)) returning id
                """, userId, jobPostingId, title, updatedAt);
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
