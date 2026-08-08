package com.cenergy.passed_backend.domain.coverletter.controller;

import com.cenergy.passed_backend.domain.coverletter.application.CompanyCoverLetterCommandService;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemUpdateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공고별 자기소개서 문항의 수정·삭제 API다.
 * 기존 첨삭 API와 같은 문항 URL 접두사를 사용하지만 HTTP method와 하위 경로가 달라 충돌하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/company-cover-letter-items")
public class CompanyCoverLetterItemController {
    private final CompanyCoverLetterCommandService commandService;

    /** 문항 명령 서비스를 주입받는다. */
    public CompanyCoverLetterItemController(CompanyCoverLetterCommandService commandService) {
        this.commandService = commandService;
    }

    /** 현재 사용자가 소유한 문항을 수정하고, 답변이 바뀌면 기존 첨삭을 무효화한다. */
    @PatchMapping("/{itemId}")
    public ResponseEntity<CompanyCoverLetterItemResponse> update(
            @PathVariable Long itemId,
            @Valid @RequestBody CompanyCoverLetterItemUpdateRequest request
    ) {
        return ResponseEntity.ok(commandService.updateItem(itemId, request));
    }

    /** 현재 사용자가 소유한 문항을 삭제한다. */
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(@PathVariable Long itemId) {
        commandService.deleteItem(itemId);
        return ResponseEntity.noContent().build();
    }
}
