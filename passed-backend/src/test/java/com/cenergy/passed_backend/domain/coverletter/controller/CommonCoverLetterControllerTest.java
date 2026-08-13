package com.cenergy.passed_backend.domain.coverletter.controller;

import com.cenergy.passed_backend.domain.coverletter.application.CommonCoverLetterService;
import com.cenergy.passed_backend.domain.coverletter.dto.requests.CommonCoverLetterUpsertRequest;
import com.cenergy.passed_backend.domain.coverletter.dto.responses.CommonCoverLetterResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommonCoverLetterControllerTest {
    private final CommonCoverLetterService service = mock(CommonCoverLetterService.class);
    private final CommonCoverLetterController controller = new CommonCoverLetterController(service);

    @Test
    void createReturns201() {
        CommonCoverLetterUpsertRequest request = new CommonCoverLetterUpsertRequest(
                List.of(new CommonCoverLetterUpsertRequest.Item(1L, "Spring Boot project experience"))
        );
        CommonCoverLetterResponse response = mock(CommonCoverLetterResponse.class);
        when(service.create(request)).thenReturn(response);

        var actual = controller.create(request);

        assertEquals(HttpStatus.CREATED, actual.getStatusCode());
        assertEquals(response, actual.getBody());
    }

    @Test
    void deleteMineReturns204() {
        var actual = controller.deleteMine();

        assertEquals(HttpStatus.NO_CONTENT, actual.getStatusCode());
        verify(service).deleteMine();
    }
}
