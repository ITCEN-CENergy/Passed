package com.cenergy.passed_backend.domain.resume.api;

import com.cenergy.passed_backend.domain.resume.application.ResumeService;
import com.cenergy.passed_backend.domain.resume.dto.ResumeResponse;
import com.cenergy.passed_backend.domain.resume.dto.ResumeUpsertRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeControllerTest {
    private final ResumeService service = mock(ResumeService.class);
    private final ResumeController controller = new ResumeController(service);

    @Test
    void createReturns201() {
        ResumeUpsertRequest request = request();
        ResumeResponse response = mock(ResumeResponse.class);
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

    private ResumeUpsertRequest request() {
        return new ResumeUpsertRequest(
                new ResumeUpsertRequest.PersonalInfoRequest(
                        LocalDate.of(2000, 1, 1), "FEMALE", "user@example.com",
                        "010-1234-5678", "Seoul", null
                ),
                List.of(new ResumeUpsertRequest.EducationRequest(
                        null, "UNIVERSITY", "Passed University", null, null,
                        "GRADUATED", false, "Computer Science", null, null, null
                )),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}
