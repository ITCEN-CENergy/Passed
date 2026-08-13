package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CoverLetterQuestionRepository extends JpaRepository<CoverLetterQuestion, Long> {
    List<CoverLetterQuestion> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();

    List<CoverLetterQuestion> findAllByIdInAndActiveTrue(Collection<Long> ids);
}
