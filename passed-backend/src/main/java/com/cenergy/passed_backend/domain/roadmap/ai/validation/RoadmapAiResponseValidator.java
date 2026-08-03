package com.cenergy.passed_backend.domain.roadmap.ai.validation;

import com.cenergy.passed_backend.domain.roadmap.ai.client.RoadmapAiException;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapAiRequest;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapAiResponse;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapAiResult;
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
        List<RoadmapAiResponse.Milestone> sorted = new ArrayList<>(skill.milestones());
        invalidIf(sorted.stream().anyMatch(item -> item == null || item.learningOrder() == null),
                "milestone and learningOrder must not be null");
        sorted.sort(Comparator.comparingInt(RoadmapAiResponse.Milestone::learningOrder));

        List<ValidatedRoadmapMilestone> milestones = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            RoadmapAiResponse.Milestone milestone = sorted.get(index);
            validateMilestone(milestone, index + 1);
            milestones.add(toValidated(milestone));
        }

        if (competency.category() == CompetencyCategory.CERTIFICATION) {
            validateCertification(milestones);
        } else {
            validateLevelPath(competency, milestones);
        }
        return new ValidatedRoadmapSkill(skill.roadmapSkillKey(), milestones);
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
        invalidIf(milestone.startLevel() == null || milestone.startLevel() < 0,
                "startLevel must be non-negative");
        invalidIf(milestone.targetLevel() == null || milestone.targetLevel() <= milestone.startLevel(),
                "targetLevel must be greater than startLevel");
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
        for (int index = 1; index < milestones.size(); index++) {
            invalidIf(milestones.get(index - 1).targetLevel() != milestones.get(index).startLevel(),
                    "milestone level path is disconnected");
        }
    }

    private void validateCertification(List<ValidatedRoadmapMilestone> milestones) {
        invalidIf(milestones.size() != 1, "CERTIFICATION must have exactly one milestone");
        ValidatedRoadmapMilestone milestone = milestones.getFirst();
        invalidIf(milestone.startLevel() != 0 || milestone.targetLevel() != 1
                        || milestone.milestoneType() != MilestoneType.CERTIFICATION
                        || milestone.learningOrder() != 1,
                "invalid CERTIFICATION milestone");
    }

    private ValidatedRoadmapMilestone toValidated(RoadmapAiResponse.Milestone milestone) {
        return new ValidatedRoadmapMilestone(
                milestone.title(), milestone.description(), milestone.learningObjective(),
                milestone.completionCriteria(), milestone.startLevel(), milestone.targetLevel(),
                milestone.milestoneType(), milestone.difficulty(), milestone.estimatedMinutes(),
                milestone.learningOrder()
        );
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
