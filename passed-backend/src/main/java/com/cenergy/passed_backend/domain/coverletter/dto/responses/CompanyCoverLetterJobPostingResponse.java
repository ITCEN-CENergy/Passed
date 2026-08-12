package com.cenergy.passed_backend.domain.coverletter.dto.responses;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;
import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterManualJobPosting;
import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;

/** 연결형과 직접 입력형을 편집 화면에 동일한 채용공고 구조로 전달한다. */
public record CompanyCoverLetterJobPostingResponse(
        Long id,
        String postingTitle,
        String companyName,
        String jobRoleName,
        String positionDetail,
        String careerType,
        String hireType,
        String mainDuty,
        String qualification,
        String preference
) {
    public static CompanyCoverLetterJobPostingResponse from(CoverLetterCompany coverLetter) {
        if (coverLetter.isManual()) {
            CoverLetterManualJobPosting posting = coverLetter.getManualJobPosting();
            return new CompanyCoverLetterJobPostingResponse(
                    null, posting.getPostingTitle(), posting.getCompanyName(), posting.getJobRoleName(),
                    posting.getPositionDetail(), posting.getCareerType(), posting.getHireType(),
                    posting.getMainDuty(), posting.getQualification(), posting.getPreference()
            );
        }
        JobPosting posting = coverLetter.getJobPosting();
        return new CompanyCoverLetterJobPostingResponse(
                posting.getId(), posting.getTitle(), posting.getCompany().getCompanyName(),
                posting.getJobRole().getJobRoleName(), posting.getPositionDetail(), posting.getCareerType(),
                posting.getHireType(), posting.getMainDuty(), posting.getQualification(), posting.getPreference()
        );
    }
}
