package com.cenergy.passed_backend.domain.jobposting.repository;

import com.cenergy.passed_backend.domain.jobposting.entity.CompanySize;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobPostingRepositoryTest {
    @Autowired
    JobPostingRepository jobPostingRepository;

    @Test
    void findsPostingsWhenOptionalTextFiltersAreEmpty() {
        assertThatCode(() -> jobPostingRepository.findFiltered(
                "", "", 0L, 0L, false, CompanySize.STARTUP,
                false, 1L, PageRequest.of(0, 12)
        )).doesNotThrowAnyException();
    }
}
