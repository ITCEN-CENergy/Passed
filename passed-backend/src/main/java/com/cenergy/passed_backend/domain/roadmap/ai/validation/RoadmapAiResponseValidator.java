package com.cenergy.passed_backend.domain.roadmap.ai.validation;

import com.cenergy.passed_backend.domain.roadmap.ai.client.RoadmapAiException;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapAiRequest;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapAiResponse;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapAiResult;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedLearningResource;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapMilestone;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapSkill;
import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneType;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.net.URI;

@Component
public class RoadmapAiResponseValidator {

    public ValidatedRoadmapAiResult validate(RoadmapAiRequest request, RoadmapAiResponse response) {
        invalidIf(request == null || request.competencies() == null || request.competencies().isEmpty(),
                "request competencies must not be empty");
        invalidIf(response == null, "response must not be null");
        requireText(response.title(), "title");
        invalidIf(response.skills() == null || response.skills().isEmpty(), "skills must not be empty");

        Map<String, RoadmapAiRequest.Competency> requested = requestedByKey(request.competencies());
        Set<String> responseKeys = new HashSet<>();
        List<ValidatedRoadmapSkill> validatedSkills = new ArrayList<>();
        for (RoadmapAiResponse.Skill skill : response.skills()) {
            invalidIf(skill == null, "skill must not be null");
            requireText(skill.roadmapSkillKey(), "roadmapSkillKey");
            invalidIf(!responseKeys.add(skill.roadmapSkillKey()), "duplicate roadmapSkillKey");
            RoadmapAiRequest.Competency competency = requested.get(skill.roadmapSkillKey());
            invalidIf(competency == null, "unexpected roadmapSkillKey");
            validatedSkills.add(validateSkill(competency, skill));
        }
        invalidIf(!responseKeys.equals(requested.keySet()), "not all requested roadmapSkillKeys were returned");

        return new ValidatedRoadmapAiResult(response.title(), validatedSkills);
    }

    private Map<String, RoadmapAiRequest.Competency> requestedByKey(
            List<RoadmapAiRequest.Competency> competencies
    ) {
        Map<String, RoadmapAiRequest.Competency> requested = new HashMap<>();
        for (RoadmapAiRequest.Competency competency : competencies) {
            invalidIf(competency == null, "request competency must not be null");
            requireText(competency.roadmapSkillKey(), "requested roadmapSkillKey");
            validateRequestedLevels(competency);
            invalidIf(requested.putIfAbsent(competency.roadmapSkillKey(), competency) != null,
                    "duplicate requested roadmapSkillKey");
        }
        return requested;
    }

    private ValidatedRoadmapSkill validateSkill(
            RoadmapAiRequest.Competency competency,
            RoadmapAiResponse.Skill skill
    ) {
        invalidIf(skill.milestones() == null || skill.milestones().isEmpty(), "milestones must not be empty");
        int maximumMilestones = competency.category() == CompetencyCategory.CERTIFICATION ? 4 : 8;
        invalidIf(skill.milestones().size() > maximumMilestones, "too many milestones");
        List<RoadmapAiResponse.Milestone> sorted = new ArrayList<>(skill.milestones());
        invalidIf(sorted.stream().anyMatch(item -> item == null || item.learningOrder() == null),
                "milestone and learningOrder must not be null");
        sorted.sort(Comparator.comparingInt(RoadmapAiResponse.Milestone::learningOrder));

        List<ValidatedRoadmapMilestone> milestones = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            RoadmapAiResponse.Milestone milestone = sorted.get(index);
            validateMilestone(milestone, index + 1);
            validateMilestoneType(competency, milestone);
            milestones.add(toValidated(milestone));
        }

        if (competency.category() == CompetencyCategory.CERTIFICATION) {
            validateCertification(competency, milestones);
        } else {
            validateLevelPath(competency, milestones);
        }
        validateRequiredMilestones(milestones);
        return new ValidatedRoadmapSkill(skill.roadmapSkillKey(), milestones);
    }

    private void validateRequiredMilestones(List<ValidatedRoadmapMilestone> milestones) {
        Map<String, Boolean> requiredByStage = new HashMap<>();
        for (ValidatedRoadmapMilestone milestone : milestones) {
            String stage = milestone.startLevel() + ":" + milestone.targetLevel();
            requiredByStage.merge(stage, milestone.required(), Boolean::logicalOr);
        }
        invalidIf(requiredByStage.values().stream().anyMatch(required -> !required),
                "each learning stage must contain a required milestone");
    }

    private void validateMilestone(RoadmapAiResponse.Milestone milestone, int expectedOrder) {
        requireText(milestone.title(), "milestone title");
        requireText(milestone.description(), "description");
        requireText(milestone.learningObjective(), "learningObjective");
        requireText(milestone.completionCriteria(), "completionCriteria");
        invalidIf(milestone.milestoneType() == null, "milestoneType must not be null");
        invalidIf(milestone.difficulty() == null, "difficulty must not be null");
        invalidIf(milestone.estimatedMinutes() == null || milestone.estimatedMinutes() < 1,
                "estimatedMinutes must be positive");
        invalidIf(milestone.learningOrder() == null || milestone.learningOrder() != expectedOrder,
                "learningOrder must start at 1 and be continuous");
        invalidIf(milestone.required() == null, "required must not be null");
        invalidIf(milestone.startLevel() == null || milestone.startLevel() < 0,
                "startLevel must be non-negative");
        invalidIf(milestone.targetLevel() == null || milestone.targetLevel() > 3,
                "targetLevel must not exceed 3");
        invalidIf(milestone.targetLevel() == null || milestone.targetLevel() < milestone.startLevel(),
                "targetLevel must be greater than or equal to startLevel");
    }

    private void validateRequestedLevels(RoadmapAiRequest.Competency competency) {
        invalidIf(competency.currentLevel() == null || competency.targetLevel() == null,
                "requested levels must not be null");
        if (competency.category() == CompetencyCategory.CERTIFICATION) {
            invalidIf((competency.currentLevel() != 0 && competency.currentLevel() != 1)
                            || competency.targetLevel() != 1,
                    "requested CERTIFICATION competency must be 0 to 1 or 1 to 1");
            return;
        }
        invalidIf(competency.currentLevel() < 0 || competency.currentLevel() > 3,
                "requested currentLevel must be between 0 and 3");
        invalidIf(competency.targetLevel() < 1 || competency.targetLevel() > 3,
                "requested targetLevel must be between 1 and 3");
        invalidIf(competency.currentLevel() > competency.targetLevel(),
                "requested currentLevel must not exceed targetLevel");
    }

    private void validateMilestoneType(
            RoadmapAiRequest.Competency competency,
            RoadmapAiResponse.Milestone milestone
    ) {
        boolean certification = competency.category() == CompetencyCategory.CERTIFICATION;
        invalidIf(certification != (milestone.milestoneType() == MilestoneType.CERTIFICATION),
                "milestoneType does not match competency category");
    }

    private void validateLevelPath(
            RoadmapAiRequest.Competency competency,
            List<ValidatedRoadmapMilestone> milestones
    ) {
        invalidIf(competency.currentLevel() == null || competency.targetLevel() == null,
                "requested levels must not be null");
        invalidIf(milestones.getFirst().startLevel() != competency.currentLevel(),
                "first startLevel does not match request");
        invalidIf(milestones.getLast().targetLevel() != competency.targetLevel(),
                "last targetLevel does not match request");
        if (competency.currentLevel().equals(competency.targetLevel())) {
            for (ValidatedRoadmapMilestone milestone : milestones) {
                invalidIf(milestone.startLevel() != competency.currentLevel()
                                || milestone.targetLevel() != competency.targetLevel(),
                        "reinforcement milestone must remain at the requested level");
            }
            validateStageCount(milestones.size());
            return;
        }
        int currentStageStart = competency.currentLevel();
        int currentStageCount = 0;
        for (ValidatedRoadmapMilestone milestone : milestones) {
            invalidIf(milestone.targetLevel() != milestone.startLevel() + 1,
                    "milestone must advance exactly one level");
            if (milestone.startLevel() == currentStageStart) {
                currentStageCount++;
                continue;
            }
            validateStageCount(currentStageCount);
            invalidIf(milestone.startLevel() != currentStageStart + 1,
                    "milestone stages must be ordered and continuous");
            currentStageStart = milestone.startLevel();
            currentStageCount = 1;
        }
        validateStageCount(currentStageCount);
    }

    private void validateCertification(
            RoadmapAiRequest.Competency competency,
            List<ValidatedRoadmapMilestone> milestones
    ) {
        validateStageCount(milestones.size());
        for (ValidatedRoadmapMilestone milestone : milestones) {
            invalidIf(milestone.startLevel() != competency.currentLevel()
                            || milestone.targetLevel() != competency.targetLevel()
                            || milestone.milestoneType() != MilestoneType.CERTIFICATION,
                    "invalid CERTIFICATION milestone");
        }
    }

    private void validateStageCount(int count) {
        invalidIf(count < 3 || count > 4,
                "each learning stage must contain 3 to 4 milestones");
    }

    private ValidatedRoadmapMilestone toValidated(RoadmapAiResponse.Milestone milestone) {
        List<ValidatedLearningResource> resources = validateResources(milestone.learningResources());
        return new ValidatedRoadmapMilestone(
                milestone.title(), milestone.description(), milestone.learningObjective(),
                milestone.completionCriteria(), milestone.startLevel(), milestone.targetLevel(),
                milestone.milestoneType(), milestone.difficulty(), milestone.estimatedMinutes(),
                milestone.learningOrder(), milestone.required(), resources
        );
    }

    private List<ValidatedLearningResource> validateResources(
            List<RoadmapAiResponse.LearningResource> resources
    ) {
        invalidIf(resources == null, "learningResources must not be null");
        invalidIf(resources.size() > 3, "a milestone may contain at most 3 learningResources");
        Set<String> resourceIds = new HashSet<>();
        List<ValidatedLearningResource> validated = new ArrayList<>();
        for (RoadmapAiResponse.LearningResource resource : resources) {
            invalidIf(resource == null, "learningResource must not be null");
            requireText(resource.resourceId(), "resourceId");
            requireText(resource.resourceType(), "resourceType");
            requireText(resource.title(), "resource title");
            requireText(resource.provider(), "resource provider");
            requireText(resource.url(), "resource url");
            invalidIf(!resourceIds.add(resource.resourceId()), "duplicate learning resourceId");
            invalidIf(!isHttpUrl(resource.url()), "learning resource url must be HTTP(S)");
            if (resource.thumbnailUrl() != null && !resource.thumbnailUrl().isBlank()) {
                invalidIf(!isHttpUrl(resource.thumbnailUrl()),
                        "learning resource thumbnailUrl must be HTTP(S)");
            }
            validated.add(new ValidatedLearningResource(
                    resource.resourceId(), resource.resourceType(), resource.title(),
                    resource.description() == null ? "" : resource.description(), resource.provider(),
                    resource.url(), resource.thumbnailUrl(), resource.authors(), resource.isFree()
            ));
        }
        return validated;
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute() && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void requireText(String value, String field) {
        invalidIf(value == null || value.isBlank(), field + " must not be blank");
    }

    private void invalidIf(boolean invalid, String message) {
        if (invalid) {
            throw new RoadmapAiException(ErrorCode.ROADMAP_AI_INVALID_RESPONSE, message);
        }
    }
}
