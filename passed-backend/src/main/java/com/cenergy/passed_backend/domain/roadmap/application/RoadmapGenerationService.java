package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.ai.client.RoadmapAiClient;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapAiRequest;
import com.cenergy.passed_backend.domain.roadmap.skillgap.merge.CompetencyGapMergeService;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.CompetencyGapMergeInput;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.MergedCompetencyGap;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.ValidatedSkillGapResult;
import com.cenergy.passed_backend.domain.roadmap.skillgap.validation.SkillGapResponseValidator;
import com.cenergy.passed_backend.domain.skillgap.application.SkillGapService;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RoadmapGenerationService {
    private final SkillGapService skillGapService;
    private final SkillGapResponseValidator skillGapValidator;
    private final CompetencyGapMergeService mergeService;
    private final RoadmapAiClient aiClient;

    public RoadmapGenerationService(SkillGapService skillGapService,
                                    SkillGapResponseValidator skillGapValidator,
                                    CompetencyGapMergeService mergeService,
                                    RoadmapAiClient aiClient) {
        this.skillGapService = skillGapService;
        this.skillGapValidator = skillGapValidator;
        this.mergeService = mergeService;
        this.aiClient = aiClient;
    }

    public RoadmapGenerationResult generate(Long userId, List<Long> jobPostingIds) {
        List<CompetencyGapMergeInput> inputs = new ArrayList<>();
        for (Long jobPostingId : jobPostingIds) {
            ValidatedSkillGapResult validated = skillGapValidator.validate(userId, jobPostingId,
                    skillGapService.getCompetencyGaps(jobPostingId, userId));
            inputs.add(new CompetencyGapMergeInput(jobPostingId, null, validated.competencyGaps()));
        }
        List<MergedCompetencyGap> gaps = mergeService.merge(inputs);
        if (gaps.isEmpty()) {
            throw new RoadmapException(ErrorCode.ROADMAP_NO_COMPETENCY_TO_LEARN,
                    "No competency gap to learn");
        }
        return RoadmapGenerationResult.combine(gaps,
                aiClient.generate(RoadmapAiRequest.from(userId, gaps)));
    }
}
