package com.cenergy.passed_backend.domain.roadmap.api;

import com.cenergy.passed_backend.domain.roadmap.application.RoadmapException;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class RoadmapExceptionHandlerTest {

    @Test
    void returnsConflictWithExistingRoadmapIdForDuplicateActiveRoadmap() {
        var response = new RoadmapExceptionHandler().roadmap(RoadmapException.duplicate(77L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.ROADMAP_ALREADY_EXISTS);
        assertThat(response.getBody().roadmapId()).isEqualTo(77L);
    }
}
