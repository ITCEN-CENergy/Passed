package com.cenergy.passed_backend.domain.user.api;

import com.cenergy.passed_backend.domain.user.application.UserJobPreferenceService;
import com.cenergy.passed_backend.domain.user.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserJobPreferenceControllerTest {

    @Test
    void delegatesPreferenceUpdateForCurrentUser() {
        UserJobPreferenceService service = mock(UserJobPreferenceService.class);
        UserJobPreferenceUpdateRequest request = new UserJobPreferenceUpdateRequest(
                8L,
                List.of(227L, 239L)
        );
        UserJobPreferenceResponse response = new UserJobPreferenceResponse(
                257L,
                new IndustryResponse(8L, "AI·개발·데이터"),
                List.of(
                        new JobRoleResponse(227L, "AI/ML엔지니어"),
                        new JobRoleResponse(239L, "AI서비스개발자")
                ),
                OffsetDateTime.parse("2026-08-11T12:00:00+09:00")
        );
        when(service.update(request)).thenReturn(response);

        var actual = new UserJobPreferenceController(service).update(request);

        assertEquals(HttpStatus.OK, actual.getStatusCode());
        assertEquals(response, actual.getBody());
    }

    @Test
    void delegatesIndustryAndJobRoleCatalogQueries() {
        UserJobPreferenceService service = mock(UserJobPreferenceService.class);
        IndustryListResponse industries = new IndustryListResponse(List.of(
                new IndustryResponse(8L, "AI·개발·데이터")
        ));
        JobRoleListResponse jobRoles = new JobRoleListResponse(
                new IndustryResponse(8L, "AI·개발·데이터"),
                List.of(new JobRoleResponse(227L, "AI/ML엔지니어"))
        );
        when(service.findIndustries()).thenReturn(industries);
        when(service.findJobRoles(8L)).thenReturn(jobRoles);
        UserJobPreferenceResponse preference = new UserJobPreferenceResponse(
                257L,
                new IndustryResponse(8L, "AI·개발·데이터"),
                List.of(new JobRoleResponse(227L, "AI/ML엔지니어")),
                OffsetDateTime.parse("2026-08-11T12:00:00+09:00")
        );
        when(service.findCurrent()).thenReturn(preference);
        UserJobPreferenceController controller = new UserJobPreferenceController(service);

        assertEquals(industries, controller.findIndustries().getBody());
        assertEquals(jobRoles, controller.findJobRoles(8L).getBody());
        assertEquals(preference, controller.findCurrent().getBody());
    }
}
