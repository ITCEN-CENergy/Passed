package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.jobposting.entity.JobPosting;
import com.cenergy.passed_backend.domain.jobposting.repository.JobPostingRepository;
import com.cenergy.passed_backend.domain.recommendation.application.model.RecommendationPostingSummary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
@Transactional(readOnly = true)
public class RecommendationPostingSummaryLoader {
    private final JobPostingRepository jobPostingRepository;

    public RecommendationPostingSummaryLoader(JobPostingRepository jobPostingRepository) {
        this.jobPostingRepository = jobPostingRepository;
    }

    public Map<Long, RecommendationPostingSummary> load(Collection<Long> jobPostingIds) {
        Objects.requireNonNull(jobPostingIds, "jobPostingIds must not be null");
        if (jobPostingIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, RecommendationPostingSummary> result = new LinkedHashMap<>();
        for (JobPosting posting : jobPostingRepository.findAllByIdIn(jobPostingIds)) {
            result.put(posting.getId(), new RecommendationPostingSummary(
                    posting.getId(),
                    posting.getTitle(),
                    posting.getCompany().getCompanyName(),
                    posting.getPositionDetail(),
                    posting.getMainDuty(),
                    posting.getQualification(),
                    posting.getPreference(),
                    posting.getCompany().getTalentProfile()
            ));
        }
        if (result.size() != jobPostingIds.size()) {
            throw new IllegalStateException("A selected job posting no longer exists");
        }
        return Map.copyOf(result);
    }
}
