package com.cenergy.passed_backend.domain.user.api;

import com.cenergy.passed_backend.domain.user.application.MyPageQueryService;
import com.cenergy.passed_backend.domain.user.dto.MyPageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyPageControllerTest {

    @Test
    void returnsCurrentUsersMyPageSummary() {
        MyPageQueryService service = mock(MyPageQueryService.class);
        MyPageResponse response = new MyPageResponse(
                "김민주",
                "kimminju@example.com",
                "/uploads/resume-photos/profile.png",
                OffsetDateTime.parse("2026-08-10T15:20:00+09:00"),
                OffsetDateTime.parse("2026-08-12T18:10:00+09:00")
        );
        when(service.findMine()).thenReturn(response);

        var actual = new MyPageController(service).findMine();

        assertEquals(HttpStatus.OK, actual.getStatusCode());
        assertEquals(response, actual.getBody());
    }
}
