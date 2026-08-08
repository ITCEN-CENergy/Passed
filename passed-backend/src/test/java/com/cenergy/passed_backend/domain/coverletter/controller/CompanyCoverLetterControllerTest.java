package com.cenergy.passed_backend.domain.coverletter.controller;

import com.cenergy.passed_backend.domain.coverletter.application.CompanyCoverLetterCommandService;
import com.cenergy.passed_backend.domain.coverletter.application.CompanyCoverLetterQueryService;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CompanyCoverLetterItemCreateRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CompanyCoverLetterDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the HTTP status contract of the company-cover-letter controller without starting Spring.
 */
class CompanyCoverLetterControllerTest {

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
