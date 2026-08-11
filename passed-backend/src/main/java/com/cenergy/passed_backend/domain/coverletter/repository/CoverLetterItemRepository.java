package com.cenergy.passed_backend.domain.coverletter.repository;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterItem;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
//조윤지: 공통 자기소개서 문항 조회를 위한 Repository - 자기소개서 답변과 질문 정보를 함께 조회합니다.
public interface CoverLetterItemRepository extends JpaRepository<CoverLetterItem, Long> {
    @EntityGraph(attributePaths = "question")
    List<CoverLetterItem> findAllByCoverLetterIdOrderByQuestion_DisplayOrderAscQuestion_IdAsc(
            Long coverLetterId
    );
}
