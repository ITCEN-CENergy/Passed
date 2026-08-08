package com.cenergy.passed_backend.domain.coverletter.dto.responses;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.user.entity.User;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * 공고별 자기소개서 응답 DTO(기존에 존재했던 응답 형태).
 * 현재 사용처가 없어 호환 목적으로만 보존하며, import 경로를 entity 패키지로 정리했다.
 * 시간 필드는 DB 타입(timestamptz)에 맞춰 OffsetDateTime을 사용한다.
 */
@Data
@Builder
@Getter
@Setter
@AllArgsConstructor
public class CoverLetterCompanyResponse {
    private Long id;
    private User user;
    private JobPosting jobPosting;
    private String title;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}