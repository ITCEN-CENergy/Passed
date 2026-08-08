package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompanyItem;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterItemFeedback;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterScore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CoverLetterFeedbackRepositoryTest {
    @Autowired
    CoverLetterCompanyItemRepository itemRepository;
    @Autowired
    CoverLetterItemFeedbackRepository feedbackRepository;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void storesOneFeedbackPerCompanyCoverLetterItemWithKoreanScore() {
        long userId = insertUser();
        long itemId = insertCompanyCoverLetterItem(userId, insertJobPosting());
        CoverLetterCompanyItem item = itemRepository.findOwnedItem(itemId, userId).orElseThrow();

        CoverLetterItemFeedback feedback = feedbackRepository.saveAndFlush(
                CoverLetterItemFeedback.create(
                        item,
                        CoverLetterScore.SUFFICIENT,
                        null,
                        "개선점",
                        "수정 답변"
                )
        );

        assertThat(jdbc.queryForObject(
                "select score from cover_letter_item_feedbacks where id = ?",
                String.class,
                feedback.getId()
        )).isEqualTo("충분");
        assertThat(feedbackRepository.findByCoverLetterCompanyItemId(itemId))
                .get()
                .extracting(CoverLetterItemFeedback::getSuggestedAnswer)
                .isEqualTo("수정 답변");

        feedback.update(CoverLetterScore.INSUFFICIENT, null, "새 개선점", "새 수정 답변");
        feedbackRepository.saveAndFlush(feedback);

        assertThat(jdbc.queryForObject(
                "select count(*) from cover_letter_item_feedbacks where cover_letter_company_item_id = ?",
                Long.class,
                itemId
        )).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select score from cover_letter_item_feedbacks where id = ?",
                String.class,
                feedback.getId()
        )).isEqualTo("미흡");
    }

    private long insertUser() {
        String unique = Long.toUnsignedString(System.nanoTime());
        return id(
                "insert into users(name, email, password) values ('user', ?, 'pw') returning id",
                unique + "@cover-letter.test"
        );
    }

    private long insertJobPosting() {
        String unique = Long.toUnsignedString(System.nanoTime());
        long industryId = id(
                "insert into industries(industry_name) values (?) returning id",
                "cover-letter-industry-" + unique
        );
        long roleId = id(
                "insert into job_roles(industry_id, job_role_name) values (?, 'role') returning id",
                industryId
        );
        long companyId = id(
                "insert into companies(company_name) values (?) returning id",
                "cover-letter-company-" + unique
        );
        return id(
                "insert into job_postings(title, company_id, job_role_id) values ('posting', ?, ?) returning id",
                companyId,
                roleId
        );
    }

    private long insertCompanyCoverLetterItem(long userId, long jobPostingId) {
        long coverLetterId = id("""
                insert into cover_letters_company(user_id, job_posting_id, title)
                values (?, ?, '지원 자기소개서') returning id
                """, userId, jobPostingId);
        return id("""
                insert into cover_letters_company_items(
                  cover_letter_company_id, question_text, answer, character_limit, display_order
                ) values (?, '지원 동기를 작성해 주세요.', '원문 답변', 700, 1) returning id
                """, coverLetterId);
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
