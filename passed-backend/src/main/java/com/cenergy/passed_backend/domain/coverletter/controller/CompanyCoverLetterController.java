package com.cenergy.passed_backend.domain.coverletter.controller;

import com.cenergy.passed_backend.domain.coverletter.application.CompanyCoverLetterCommandService;
import com.cenergy.passed_backend.domain.coverletter.application.CompanyCoverLetterQueryService;
import com.cenergy.passed_backend.domain.coverletter.application.CoverLetterOverallFeedbackService;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterReplaceRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterUpdateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.ManualCompanyCoverLetterCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterDetailResponse;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterItemResponse;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterSummaryResponse;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CoverLetterOverallFeedbackResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 현재 사용자의 공고별 자기소개서 CRUD HTTP API다.
 * 사용자 식별자는 URL이나 요청 본문에서 받지 않고 서비스의 CurrentUserIdProvider로만 결정한다.
 */
@RestController
@RequestMapping("/api/v1/company-cover-letters")
public class CompanyCoverLetterController {
    private final CompanyCoverLetterCommandService commandService;
    private final CompanyCoverLetterQueryService queryService;
    private final CoverLetterOverallFeedbackService overallFeedbackService;

    public CompanyCoverLetterController(
            CompanyCoverLetterCommandService commandService,
            CompanyCoverLetterQueryService queryService
    ) {
        this(commandService, queryService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CompanyCoverLetterController(
            CompanyCoverLetterCommandService commandService,
            CompanyCoverLetterQueryService queryService,
            CoverLetterOverallFeedbackService overallFeedbackService
    ) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.overallFeedbackService = overallFeedbackService;
    }

    /** 현재 사용자의 새 공고별 자기소개서와 최초 문항을 생성한다. */
    @PostMapping
    public ResponseEntity<CompanyCoverLetterDetailResponse> create(
            @Valid @RequestBody CompanyCoverLetterCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commandService.create(request));
    }

    /** 자기소개서 목록에서 직접 입력한 공고와 자기소개서를 생성한다. */
    @PostMapping("/manual")
    public ResponseEntity<CompanyCoverLetterDetailResponse> createManual(
            @Valid @RequestBody ManualCompanyCoverLetterCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commandService.createManual(request));
    }

    /** 현재 사용자가 소유한 공고별 자기소개서 목록을 반환한다. */
    @GetMapping
    public ResponseEntity<Page<CompanyCoverLetterSummaryResponse>> findAll(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(queryService.findAll(pageable));
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

    /** 편집 화면의 제목, 공고 정보, 전체 문항을 한 번에 저장한다. */
    @PutMapping("/{coverLetterId}")
    public ResponseEntity<CompanyCoverLetterDetailResponse> replace(
            @PathVariable Long coverLetterId,
            @Valid @RequestBody CompanyCoverLetterReplaceRequest request
    ) {
        return ResponseEntity.ok(commandService.replace(coverLetterId, request));
    }

    /** 현재 사용자가 소유한 자기소개서를 삭제한다. */
    @DeleteMapping("/{coverLetterId}")
    public ResponseEntity<Void> delete(@PathVariable Long coverLetterId) {
        commandService.delete(coverLetterId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{coverLetterId}/feedback")
    public ResponseEntity<CoverLetterOverallFeedbackResponse> generateOverallFeedback(
            @PathVariable Long coverLetterId
    ) {
        return ResponseEntity.ok(overallFeedbackService.generate(coverLetterId));
    }

    @GetMapping("/{coverLetterId}/feedback")
    public ResponseEntity<CoverLetterOverallFeedbackResponse> findOverallFeedback(
            @PathVariable Long coverLetterId
    ) {
        return ResponseEntity.ok(overallFeedbackService.find(coverLetterId));
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
