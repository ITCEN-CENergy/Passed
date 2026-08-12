package com.cenergy.passed_backend.domain.coverletter.controller;

import com.cenergy.passed_backend.domain.coverletter.application.CompanyCoverLetterCommandService;
import com.cenergy.passed_backend.domain.coverletter.application.CompanyCoverLetterQueryService;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterDetailResponse;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the HTTP status contract of the company-cover-letter controller without starting Spring.
 */
class CompanyCoverLetterControllerTest {

    /** 목록 조회는 현재 사용자의 자기소개서 요약 목록과 HTTP 200을 그대로 반환한다. */
    @Test
    void returnsCompanyCoverLetterSummariesWithOkStatus() {
        CompanyCoverLetterCommandService commandService = mock(CompanyCoverLetterCommandService.class);
        CompanyCoverLetterQueryService queryService = mock(CompanyCoverLetterQueryService.class);
        CompanyCoverLetterController controller = new CompanyCoverLetterController(commandService, queryService);
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-08-12T09:30:00+09:00");
        List<CompanyCoverLetterSummaryResponse> response = List.of(
                new CompanyCoverLetterSummaryResponse(
                        1L,
                        301L,
                        "카카오",
                        "프론트엔드 개발자",
                        "카카오 프론트엔드 지원용 자소서",
                        updatedAt.minusDays(1),
                        updatedAt
                )
        );
        when(queryService.findAll()).thenReturn(response);

        var actual = controller.findAll();

        assertEquals(HttpStatus.OK, actual.getStatusCode());
        assertEquals(response, actual.getBody());
    }

    /** A successful create command returns HTTP 201 and the service response body unchanged. */
    @Test
    void createsCompanyCoverLetterWithCreatedStatus() {
        CompanyCoverLetterCommandService commandService = mock(CompanyCoverLetterCommandService.class);
        CompanyCoverLetterQueryService queryService = mock(CompanyCoverLetterQueryService.class);
        CompanyCoverLetterController controller = new CompanyCoverLetterController(commandService, queryService);
        CompanyCoverLetterCreateRequest request = new CompanyCoverLetterCreateRequest(
                301L,
                "Backend application",
                List.of(new CompanyCoverLetterItemCreateRequest("Why this role?", "", 1000, 1))
        );
        CompanyCoverLetterDetailResponse response = new CompanyCoverLetterDetailResponse(
                1L, 301L, "Passed", "Backend Developer", "Backend application", List.of(), null, null
        );
        when(commandService.create(request)).thenReturn(response);

        var actual = controller.create(request);

        assertEquals(HttpStatus.CREATED, actual.getStatusCode());
        assertEquals(response, actual.getBody());
    }
}
