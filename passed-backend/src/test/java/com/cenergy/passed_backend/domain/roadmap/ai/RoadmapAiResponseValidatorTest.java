package com.cenergy.passed_backend.domain.roadmap.ai;

import com.cenergy.passed_backend.domain.roadmap.ai.client.RoadmapAiException;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapAiRequest;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapAiResponse;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapAiResult;
import com.cenergy.passed_backend.domain.roadmap.ai.validation.RoadmapAiResponseValidator;
import com.cenergy.passed_backend.domain.roadmap.entity.CompetencyCategory;
import com.cenergy.passed_backend.domain.roadmap.entity.Difficulty;
import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneType;
import com.cenergy.passed_backend.domain.roadmap.entity.RequirementType;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoadmapAiResponseValidatorTest {
    private final RoadmapAiResponseValidator validator = new RoadmapAiResponseValidator();

    @Test
    void validatesAndSortsMilestonesByLearningOrder() {
        RoadmapAiResponse response = response(skill("docker",
                milestone(5, 2, 3), milestone(2, 1, 2), milestone(4, 2, 3),
                milestone(1, 1, 2), milestone(6, 2, 3), milestone(3, 1, 2)));

        ValidatedRoadmapAiResult result = validator.validate(request(technical("docker", 1, 3)), response);

        assertThat(result.title()).isEqualTo("개인 맞춤 역량 강화 로드맵");
        assertThat(result.skills()).singleElement().satisfies(skill -> {
            assertThat(skill.roadmapSkillKey()).isEqualTo("docker");
            assertThat(skill.milestones()).extracting("learningOrder")
                    .containsExactly(1, 2, 3, 4, 5, 6);
        });
    }

    @Test
    void allowsMultipleMilestonesForTheSameLearningStage() {
        RoadmapAiResponse response = response(skill("docker",
                milestone(1, 1, 2), milestone(2, 1, 2), milestone(3, 1, 2),
                milestone(4, 2, 3), milestone(5, 2, 3), milestone(6, 2, 3)));

        ValidatedRoadmapAiResult result = validator.validate(request(technical("docker", 1, 3)), response);

        assertThat(result.skills().getFirst().milestones())
                .extracting("startLevel", "targetLevel")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 2),
                        org.assertj.core.groups.Tuple.tuple(1, 2),
                        org.assertj.core.groups.Tuple.tuple(1, 2),
                        org.assertj.core.groups.Tuple.tuple(2, 3),
                        org.assertj.core.groups.Tuple.tuple(2, 3),
                        org.assertj.core.groups.Tuple.tuple(2, 3));
    }

    @Test
    void rejectsReturningToAnEarlierLearningStage() {
        assertInvalid(request(technical("docker", 1, 3)), response(skill("docker",
                milestone(1, 1, 2), milestone(2, 2, 3), milestone(3, 1, 2))));
    }

    @Test
    void validatesAllRequestedSkillsExactlyOnce() {
        RoadmapAiRequest request = request(technical("docker", 1, 2), technical("aws", 2, 3));
        RoadmapAiResponse response = response(
                skill("docker", milestone(1, 1, 2), milestone(2, 1, 2), milestone(3, 1, 2)),
                skill("aws", milestone(1, 2, 3), milestone(2, 2, 3), milestone(3, 2, 3))
        );

        assertThat(validator.validate(request, response).skills()).hasSize(2);
    }

    @Test void rejectsNullResponse() { assertInvalid(request(technical("docker", 1, 2)), null); }
    @Test void rejectsBlankTitle() { assertInvalid(request(technical("docker", 1, 2)), new RoadmapAiResponse(" ", List.of(skill("docker", milestone(1, 1, 2))))); }
    @Test void rejectsNullSkills() { assertInvalid(request(technical("docker", 1, 2)), new RoadmapAiResponse("title", null)); }
    @Test void rejectsEmptySkills() { assertInvalid(request(technical("docker", 1, 2)), new RoadmapAiResponse("title", List.of())); }
    @Test void rejectsMissingKey() { assertInvalid(request(technical("docker", 1, 2), technical("aws", 2, 3)), response(skill("docker", milestone(1, 1, 2)))); }
    @Test void rejectsUnexpectedKey() { assertInvalid(request(technical("docker", 1, 2)), response(skill("aws", milestone(1, 1, 2)))); }
    @Test void rejectsDuplicateKey() { assertInvalid(request(technical("docker", 1, 2)), response(skill("docker", milestone(1, 1, 2)), skill("docker", milestone(1, 1, 2)))); }
    @Test void rejectsNullMilestones() { assertInvalid(request(technical("docker", 1, 2)), response(new RoadmapAiResponse.Skill("docker", null))); }
    @Test void rejectsEmptyMilestones() { assertInvalid(request(technical("docker", 1, 2)), response(skill("docker"))); }

    @Test
    void rejectsBlankRequiredMilestoneString() {
        RoadmapAiResponse.Milestone value = milestone(1, 1, 2);
        RoadmapAiResponse.Milestone invalid = new RoadmapAiResponse.Milestone(
                " ", value.description(), value.learningObjective(), value.completionCriteria(),
                value.startLevel(), value.targetLevel(), value.milestoneType(), value.difficulty(),
                value.estimatedMinutes(), value.learningOrder());
        assertInvalid(request(technical("docker", 1, 2)), response(skill("docker", invalid)));
    }

    @Test
    void rejectsInvalidEstimatedMinutes() {
        RoadmapAiResponse.Milestone value = milestone(1, 1, 2);
        assertInvalidMilestone(value.startLevel(), value.targetLevel(), value.learningOrder(), 0);
    }

    @Test void rejectsLearningOrderNotStartingAtOne() { assertInvalidPath(milestone(2, 1, 2)); }
    @Test void rejectsDuplicateLearningOrder() { assertInvalidPath(milestone(1, 1, 2), milestone(1, 2, 3)); }
    @Test void rejectsMissingLearningOrder() { assertInvalidPath(milestone(1, 1, 2), milestone(3, 2, 3)); }
    @Test void rejectsNegativeStartLevel() { assertInvalidPath(milestone(1, -1, 2)); }
    @Test void rejectsTargetBelowStart() { assertInvalidPath(milestone(1, 2, 1)); }
    @Test void rejectsMissingMilestoneType() { assertInvalidPath(withTypeAndDifficulty(null, Difficulty.BEGINNER)); }
    @Test void rejectsMissingDifficulty() { assertInvalidPath(withTypeAndDifficulty(MilestoneType.CONCEPT, null)); }
    @Test void rejectsFirstLevelMismatch() { assertInvalidPath(milestone(1, 0, 2), milestone(2, 2, 3)); }
    @Test void rejectsLastLevelMismatch() { assertInvalidPath(milestone(1, 1, 2)); }
    @Test void rejectsDisconnectedMiddleLevel() { assertInvalidPath(milestone(1, 1, 2), milestone(2, 3, 4)); }
    @Test
    void validatesNonCertificationGapFromZero() {
        assertThat(validator.validate(request(technical("docker", 0, 1)), response(skill("docker",
                milestone(1, 0, 1), milestone(2, 0, 1), milestone(3, 0, 1))))
                .skills()).hasSize(1);
    }

    @Test
    void validatesThreeStagesFromZeroToThree() {
        assertThat(validator.validate(request(technical("docker", 0, 3)), response(skill("docker",
                milestone(1, 0, 1), milestone(2, 0, 1), milestone(3, 0, 1),
                milestone(4, 1, 2), milestone(5, 1, 2), milestone(6, 1, 2),
                milestone(7, 2, 3), milestone(8, 2, 3), milestone(9, 2, 3))))
                .skills().getFirst().milestones()).hasSize(9);
    }
    @Test void rejectsLevelAboveThree() { assertInvalid(request(technical("docker", 2, 4)), response(skill("docker", milestone(1, 2, 4)))); }
    @Test void rejectsCertificationMilestoneForGeneralSkill() {
        RoadmapAiResponse.Milestone value = new RoadmapAiResponse.Milestone(
                "Docker", "설명", "목표", "기준", 1, 2,
                MilestoneType.CERTIFICATION, Difficulty.BEGINNER, 60, 1);
        assertInvalid(request(technical("docker", 1, 2)), response(skill("docker", value)));
    }

    @Test
    void validatesCertificationGapFromZeroToOne() {
        RoadmapAiRequest request = request(certification("sqld", 0));

        assertThat(validator.validate(request, response(skill("sqld",
                certificationMilestone(1), certificationMilestone(2), certificationMilestone(3))))
                .skills()).hasSize(1);
    }

    @Test
    void validatesReinforcementAtTheSameLevel() {
        RoadmapAiRequest request = request(technical("docker", 3, 3));

        assertThat(validator.validate(request, response(skill("docker",
                milestone(1, 3, 3), milestone(2, 3, 3), milestone(3, 3, 3))))
                .skills()).hasSize(1);
    }

    @Test
    void validatesAchievedCertificationReinforcement() {
        RoadmapAiRequest request = request(certification("sqld", 1));

        assertThat(validator.validate(request, response(skill("sqld",
                certificationMilestone(1, 1), certificationMilestone(2, 1),
                certificationMilestone(3, 1))))
                .skills()).hasSize(1);
    }

    @Test
    void allowsMultipleCertificationMilestones() {
        RoadmapAiRequest request = request(certification("sqld", 0));
        assertThat(validator.validate(request, response(skill("sqld",
                        certificationMilestone(1), certificationMilestone(2), certificationMilestone(3))))
                .skills().getFirst().milestones()).hasSize(3);
    }

    @Test
    void rejectsCertificationMilestoneWithWrongStartLevel() {
        RoadmapAiRequest request = request(certification("sqld", 0));
        RoadmapAiResponse.Milestone certification = new RoadmapAiResponse.Milestone(
                "SQLD 자격 취득", "설명", "목표", "기준", 1, 1,
                MilestoneType.CERTIFICATION, Difficulty.BEGINNER, 60, 1);
        assertInvalid(request, response(skill("sqld", certification)));
    }

    private void assertInvalidPath(RoadmapAiResponse.Milestone... milestones) {
        assertInvalid(request(technical("docker", 1, 3)), response(skill("docker", milestones)));
    }

    private void assertInvalidMilestone(Integer start, Integer target, Integer order, Integer minutes) {
        RoadmapAiResponse.Milestone value = milestone(order, start, target);
        RoadmapAiResponse.Milestone invalid = new RoadmapAiResponse.Milestone(
                value.title(), value.description(), value.learningObjective(), value.completionCriteria(),
                start, target, value.milestoneType(), value.difficulty(), minutes, order);
        assertInvalid(request(technical("docker", 1, 2)), response(skill("docker", invalid)));
    }

    private void assertInvalid(RoadmapAiRequest request, RoadmapAiResponse response) {
        assertThatThrownBy(() -> validator.validate(request, response))
                .isInstanceOf(RoadmapAiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROADMAP_AI_INVALID_RESPONSE);
    }

    private RoadmapAiRequest request(RoadmapAiRequest.Competency... competencies) {
        return new RoadmapAiRequest(10L, List.of(competencies));
    }

    private RoadmapAiRequest.Competency technical(String key, int current, int target) {
        return competency(key, CompetencyCategory.TECHNICAL_SKILL, current, target);
    }

    private RoadmapAiRequest.Competency certification(String key, int current) {
        return competency(key, CompetencyCategory.CERTIFICATION, current, 1);
    }

    private RoadmapAiRequest.Competency competency(
            String key, CompetencyCategory category, int current, int target
    ) {
        return new RoadmapAiRequest.Competency(
                key, 1L, "Docker", category, current, target, RequirementType.REQUIRED,
                Math.max(target - current, 0), 1, 1,
                List.of(new RoadmapAiRequest.Source(101L, "경험"))
        );
    }

    private RoadmapAiResponse response(RoadmapAiResponse.Skill... skills) {
        return new RoadmapAiResponse("개인 맞춤 역량 강화 로드맵", List.of(skills));
    }

    private RoadmapAiResponse.Skill skill(String key, RoadmapAiResponse.Milestone... milestones) {
        return new RoadmapAiResponse.Skill(key, Arrays.asList(milestones));
    }

    private RoadmapAiResponse.Milestone milestone(Integer order, Integer start, Integer target) {
        return new RoadmapAiResponse.Milestone(
                "Docker 목표 수준 " + target, "설명", "목표", "기준", start, target,
                MilestoneType.CONCEPT, Difficulty.BEGINNER, 60, order
        );
    }

    private RoadmapAiResponse.Milestone certificationMilestone(int order) {
        return certificationMilestone(order, 0);
    }

    private RoadmapAiResponse.Milestone certificationMilestone(int order, int startLevel) {
        return new RoadmapAiResponse.Milestone(
                "SQLD 자격 취득 " + order, "설명", "목표", "기준", startLevel, 1,
                MilestoneType.CERTIFICATION, Difficulty.BEGINNER, 60, order);
    }

    private RoadmapAiResponse.Milestone withTypeAndDifficulty(
            MilestoneType type, Difficulty difficulty
    ) {
        RoadmapAiResponse.Milestone value = milestone(1, 1, 3);
        return new RoadmapAiResponse.Milestone(
                value.title(), value.description(), value.learningObjective(), value.completionCriteria(),
                value.startLevel(), value.targetLevel(), type, difficulty,
                value.estimatedMinutes(), value.learningOrder()
        );
    }
}
