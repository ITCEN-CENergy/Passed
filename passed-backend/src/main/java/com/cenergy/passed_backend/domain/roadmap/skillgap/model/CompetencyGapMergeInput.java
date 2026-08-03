package com.cenergy.passed_backend.domain.roadmap.skillgap.model;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public record CompetencyGapMergeInput(
        Long jobPostingId,
        Long reportId,
        List<ValidatedCompetencyGap> competencyGaps
) {
    public CompetencyGapMergeInput {
        if (competencyGaps != null) {
            competencyGaps = Collections.unmodifiableList(new ArrayList<>(competencyGaps));
        }
    }
}
