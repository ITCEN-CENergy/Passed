package com.cenergy.passed_backend.domain.jobposting.api;

import com.cenergy.passed_backend.domain.jobposting.application.JobPostingCommandService;
import com.cenergy.passed_backend.domain.jobposting.application.JobPostingQueryService;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingCreateRequest;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingCreateResponse;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingDetailResponse;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingListRequest;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingListResponse;
import com.cenergy.passed_backend.domain.jobposting.dto.JobPostingSkillCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPostingControllerTest {
    private final JobPostingQueryService queryService = mock(JobPostingQueryService.class);
    private final JobPostingCommandService commandService = mock(JobPostingCommandService.class);
    private final JobPostingController controller = new JobPostingController(
            queryService,
            commandService
    );

    @Test
    void delegatesListAndDetailQueries() {
        JobPostingListRequest request = new JobPostingListRequest(0, 10);
        JobPostingListResponse listResponse = new JobPostingListResponse(List.of(), 0, 10, 0, 0);
        JobPostingDetailResponse detailResponse = detailResponse();
        when(queryService.findAll(request)).thenReturn(listResponse);
        when(queryService.findById(100L)).thenReturn(detailResponse);

        var list = controller.getJobPostings(request);
        var detail = controller.getJobPosting(100L);

        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertEquals(listResponse, list.getBody());
        assertEquals(HttpStatus.OK, detail.getStatusCode());
        assertEquals(detailResponse, detail.getBody());
        verify(queryService).findAll(request);
        verify(queryService).findById(100L);
    }

    @Test
    void returnsCreatedForJobPostingRegistration() {
        JobPostingCreateRequest request = createRequest();
        JobPostingCreateResponse response = new JobPostingCreateResponse(100L, 1, 1);
        when(commandService.create(request)).thenReturn(response);

        var actual = controller.createJobPosting(request);

        assertEquals(HttpStatus.CREATED, actual.getStatusCode());
        assertEquals(response, actual.getBody());
        verify(commandService).create(request);
    }

    private JobPostingCreateRequest createRequest() {
        return new JobPostingCreateRequest(
                "백엔드 개발자", 1L, 2L, "20260812", "20260831", 1,
                "신입", "정규직", "서울", "학사", "포지션", "주요 업무",
                "자격요건", "우대사항", null, "서류 전형",
                List.of(new JobPostingSkillCreateRequest(10L, (short) 2)),
                List.of(new JobPostingSkillCreateRequest(20L, (short) 1))
        );
    }

    private JobPostingDetailResponse detailResponse() {
        return new JobPostingDetailResponse(
                100L, "백엔드 개발자", "IT", "서버 개발", "테스트 회사", "스타트업",
                "서울", "신입", "정규직", "학사", "포지션", "주요 업무",
                "자격요건", "우대사항", null, "서류 전형", "복지"
        );
    }
}
