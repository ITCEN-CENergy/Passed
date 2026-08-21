package com.cenergy.passed_backend.domain.coverletter.application;

import com.cenergy.passed_backend.domain.coverletter.entity.CoverLetterCompany;

/** Resolves the linked company's talent profile without changing job-posting text. */
final class CompanyTalentProfileResolver {
    private CompanyTalentProfileResolver() {
    }

    static String resolve(CoverLetterCompany coverLetter) {
        if (coverLetter == null || coverLetter.isManual()
                || coverLetter.getJobPosting() == null
                || coverLetter.getJobPosting().getCompany() == null) {
            return null;
        }
        String talentProfile = coverLetter.getJobPosting().getCompany().getTalentProfile();
        return talentProfile == null || talentProfile.isBlank() ? null : talentProfile.trim();
    }
}
