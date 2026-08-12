package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.application.CompanyCoverLetterCommandService;
import com.cenergy.passed_backend.domain.coverletter.application.CompanyCoverLetterQueryService;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemReplaceRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterReplaceRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.ManualCompanyCoverLetterCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.ManualJobPostingRequest;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterItemFeedback;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterScore;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 자기소개서 목록의 소유권, 정렬, 최종 수정일 저장 규칙을 실제 JPA 쿼리로 검증한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CoverLetterCompanyRepositoryTest {
    @Autowired
    CoverLetterCompanyRepository coverLetterRepository;

    @Autowired
    CoverLetterCompanyItemRepository itemRepository;

    @Autowired
    CoverLetterItemFeedbackRepository feedbackRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JobPostingRepository jobPostingRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void returnsOnlyOwnedCoverLettersInLatestUpdatedOrder() {
        long userId = insertUser("owner");
        long otherUserId = insertUser("other");
        long olderId = insertCoverLetter(userId, insertJobPosting("older"), "이전 자기소개서", "2026-08-10T10:00:00+09:00");
        long latestId = insertCoverLetter(userId, insertJobPosting("latest"), "최근 자기소개서", "2026-08-12T10:00:00+09:00");
        insertCoverLetter(otherUserId, insertJobPosting("hidden"), "다른 사용자 자기소개서", "2026-08-13T10:00:00+09:00");

        var result = coverLetterRepository.findAllOwnedSummary(userId, PageRequest.of(0, 20)).getContent();

        assertThat(result)
                .extracting(response -> response.id())
                .containsExactly(latestId, olderId);
        assertThat(result)
                .extracting(response -> response.title())
                .containsExactly("최근 자기소개서", "이전 자기소개서");
        var firstPage = coverLetterRepository.findAllOwnedSummary(userId, PageRequest.of(0, 1));
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent())
                .extracting(response -> response.id())
                .containsExactly(latestId);
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

    @Test
    void returnsManualCoverLetterWithoutAJobPostingRow() {
        long userId = insertUser("manual-owner");
        long manualPostingId = id("""
                insert into cover_letter_manual_job_postings(
                  posting_title, company_name, job_role_name, main_duty, qualification
                ) values ('직접 입력 공고', '직접 입력 기업', '백엔드 개발자', 'API 개발', 'Java 경험')
                returning id
                """);
        long coverLetterId = id("""
                insert into cover_letters_company(user_id, manual_job_posting_id, title)
                values (?, ?, '직접 입력 자기소개서') returning id
                """, userId, manualPostingId);

        CoverLetterCompany result = coverLetterRepository.findOwnedDetail(coverLetterId, userId).orElseThrow();

        assertThat(result.getJobPosting()).isNull();
        assertThat(result.isManual()).isTrue();
        assertThat(result.getManualJobPosting().getCompanyName()).isEqualTo("직접 입력 기업");
        assertThat(coverLetterRepository.findAllOwnedSummary(userId, PageRequest.of(0, 20)).getContent())
                .extracting(response -> response.id())
                .containsExactly(coverLetterId);
    }

    @Test
    void rejectsCoverLetterWithoutExactlyOnePostingSource() {
        long userId = insertUser("invalid-source");

        assertThatThrownBy(() -> jdbc.update(
                "insert into cover_letters_company(user_id, title) values (?, '잘못된 자기소개서')",
                userId
        )).hasMessageContaining("ck_cover_letters_company_exactly_one_posting");
    }

    @Test
    void deletesManualPostingTogetherWithCoverLetter() {
        long userId = insertUser("manual-delete");
        long manualPostingId = id("""
                insert into cover_letter_manual_job_postings(posting_title, company_name, job_role_name)
                values ('삭제 공고', '삭제 기업', '개발자') returning id
                """);
        long coverLetterId = id("""
                insert into cover_letters_company(user_id, manual_job_posting_id, title)
                values (?, ?, '삭제 자기소개서') returning id
                """, userId, manualPostingId);
        CoverLetterCompany coverLetter = coverLetterRepository
                .findOwnedDetail(coverLetterId, userId)
                .orElseThrow();

        coverLetterRepository.delete(coverLetter);
        coverLetterRepository.flush();

        assertThat(jdbc.queryForObject(
                "select count(*) from cover_letter_manual_job_postings where id = ?",
                Long.class,
                manualPostingId
        )).isZero();
    }

    @Test
    void deletesPreviousManualPostingWhenForeignKeyChanges() {
        long userId = insertUser("manual-replace");
        long previousPostingId = id("""
                insert into cover_letter_manual_job_postings(posting_title, job_role_name)
                values ('이전 공고', '개발자') returning id
                """);
        long nextPostingId = id("""
                insert into cover_letter_manual_job_postings(posting_title, job_role_name)
                values ('다음 공고', '개발자') returning id
                """);
        long coverLetterId = id("""
                insert into cover_letters_company(user_id, manual_job_posting_id, title)
                values (?, ?, 'FK 교체 자기소개서') returning id
                """, userId, previousPostingId);

        jdbc.update(
                "update cover_letters_company set manual_job_posting_id = ? where id = ?",
                nextPostingId,
                coverLetterId
        );

        assertThat(jdbc.queryForObject(
                "select count(*) from cover_letter_manual_job_postings where id = ?",
                Long.class,
                previousPostingId
        )).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from cover_letter_manual_job_postings where id = ?",
                Long.class,
                nextPostingId
        )).isEqualTo(1L);
    }

    @Test
    void generatesCompanyNameThenSequentialTitlesWhenTitleIsMissing() {
        long userId = insertUser("manual-title");
        CompanyCoverLetterQueryService queryService = new CompanyCoverLetterQueryService(
                () -> userId, coverLetterRepository, itemRepository
        );
        CompanyCoverLetterCommandService commandService = new CompanyCoverLetterCommandService(
                () -> userId, userRepository, jobPostingRepository, coverLetterRepository,
                itemRepository, feedbackRepository, queryService
        );

        var companyTitle = commandService.createManual(manualCreateRequest("회사명 공고", "테스트 기업"));
        var firstNumbered = commandService.createManual(manualCreateRequest("번호 공고 1", null));
        var secondNumbered = commandService.createManual(manualCreateRequest("번호 공고 2", "  "));

        assertThat(companyTitle.title()).isEqualTo("테스트 기업");
        assertThat(firstNumbered.title()).isEqualTo("자기소개서 1");
        assertThat(secondNumbered.title()).isEqualTo("자기소개서 2");
    }

    @Test
    void createsAndReplacesManualCoverLetterWithSwappedItemOrder() {
        long userId = insertUser("manual-service");
        CompanyCoverLetterQueryService queryService = new CompanyCoverLetterQueryService(
                () -> userId, coverLetterRepository, itemRepository
        );
        CompanyCoverLetterCommandService commandService = new CompanyCoverLetterCommandService(
                () -> userId, userRepository, jobPostingRepository, coverLetterRepository,
                itemRepository, feedbackRepository, queryService
        );
        var created = commandService.createManual(new ManualCompanyCoverLetterCreateRequest(
                null,
                manualPosting("최초 공고", "최초 업무"),
                List.of(
                        new CompanyCoverLetterItemCreateRequest("첫 질문", "첫 답변", 1000, 1),
                        new CompanyCoverLetterItemCreateRequest("둘째 질문", "둘째 답변", 1000, 2)
                )
        ));
        coverLetterRepository.flush();
        Long firstId = created.items().get(0).id();
        Long secondId = created.items().get(1).id();
        feedbackRepository.saveAndFlush(CoverLetterItemFeedback.create(
                itemRepository.findOwnedItem(firstId, userId).orElseThrow(),
                CoverLetterScore.SUFFICIENT,
                null,
                "기존 개선점",
                "기존 수정안"
        ));

        var replaced = commandService.replace(created.id(), new CompanyCoverLetterReplaceRequest(
                "수정된 자기소개서",
                manualPosting("수정 공고", "수정 업무"),
                List.of(
                        new CompanyCoverLetterItemReplaceRequest(null, "새 질문", "새 답변", 700, 3),
                        new CompanyCoverLetterItemReplaceRequest(secondId, "둘째 질문", "둘째 답변", 1000, 1),
                        new CompanyCoverLetterItemReplaceRequest(firstId, "첫 질문", "수정된 답변", 1000, 2)
                )
        ));

        assertThat(replaced.manual()).isTrue();
        assertThat(replaced.title()).isEqualTo("수정된 자기소개서");
        assertThat(replaced.jobPosting().postingTitle()).isEqualTo("수정 공고");
        assertThat(replaced.items())
                .extracting(item -> item.displayOrder())
                .containsExactly(1, 2, 3);
        assertThat(feedbackRepository.findByCoverLetterCompanyItemId(firstId)).isEmpty();
    }

    private ManualJobPostingRequest manualPosting(String postingTitle, String mainDuty) {
        return new ManualJobPostingRequest(
                postingTitle, "직접 입력 기업", "백엔드 개발자", null,
                "신입", "정규직", mainDuty, "Java 경험", "Spring 경험"
        );
    }

    private ManualCompanyCoverLetterCreateRequest manualCreateRequest(String postingTitle, String companyName) {
        return new ManualCompanyCoverLetterCreateRequest(
                null,
                new ManualJobPostingRequest(
                        postingTitle, companyName, "백엔드 개발자", null,
                        null, null, null, null, null
                ),
                List.of(new CompanyCoverLetterItemCreateRequest("지원 동기", "", 1000, 1))
        );
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
