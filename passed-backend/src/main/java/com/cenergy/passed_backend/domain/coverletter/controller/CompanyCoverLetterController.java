package com.cenergy.passed_backend.domain.coverletter.controller;

import com.cenergy.passed_backend.domain.coverletter.application.CompanyCoverLetterCommandService;
import com.cenergy.passed_backend.domain.coverletter.application.CompanyCoverLetterQueryService;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterUpdateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterDetailResponse;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterItemResponse;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 현재 사용자의 공고별 자기소개서 CRUD HTTP API다.
 * 사용자 식별자는 URL이나 요청 본문에서 받지 않고 서비스의 CurrentUserIdProvider로만 결정한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/company-cover-letters")
public class CompanyCoverLetterController {
    private final CompanyCoverLetterCommandService commandService;
    private final CompanyCoverLetterQueryService queryService;

    /** 현재 사용자의 새 공고별 자기소개서와 최초 문항을 생성한다. */
    @PostMapping
    public ResponseEntity<CompanyCoverLetterDetailResponse> create(
            @Valid @RequestBody CompanyCoverLetterCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commandService.create(request));
    }

    /** 현재 사용자가 소유한 공고별 자기소개서 목록을 반환한다. */
    @GetMapping
    public ResponseEntity<List<CompanyCoverLetterSummaryResponse>> findAll() {
        return ResponseEntity.ok(queryService.findAll());
    }

    /** 현재 사용자가 소유한 한 자기소개서와 문항 목록을 반환한다. */
    @GetMapping("/{coverLetterId}")
    public ResponseEntity<CompanyCoverLetterDetailResponse> findById(
            @PathVariable Long coverLetterId
    ) {
        return ResponseEntity.ok(queryService.findById(coverLetterId));
    }

    /** 현재 사용자가 소유한 자기소개서의 제목을 변경한다. */
    @PatchMapping("/{coverLetterId}")
    public ResponseEntity<CompanyCoverLetterDetailResponse> updateTitle(
            @PathVariable Long coverLetterId,
            @Valid @RequestBody CompanyCoverLetterUpdateRequest request
    ) {
        return ResponseEntity.ok(commandService.updateTitle(coverLetterId, request));
    }

    /** 현재 사용자가 소유한 자기소개서를 삭제한다. */
    @DeleteMapping("/{coverLetterId}")
    public ResponseEntity<Void> delete(@PathVariable Long coverLetterId) {
        commandService.delete(coverLetterId);
        return ResponseEntity.noContent().build();
    }

    /** 현재 사용자가 소유한 자기소개서에 새 문항을 추가한다. */
    @PostMapping("/{coverLetterId}/items")
    public ResponseEntity<CompanyCoverLetterItemResponse> addItem(
            @PathVariable Long coverLetterId,
            @Valid @RequestBody CompanyCoverLetterItemCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commandService.addItem(coverLetterId, request));
    }
}
