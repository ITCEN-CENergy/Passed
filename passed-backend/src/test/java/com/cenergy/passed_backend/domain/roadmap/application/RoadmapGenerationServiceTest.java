package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.ai.client.RoadmapAiClient;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapAiRequest;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapAiResult;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapMilestone;
import com.cenergy.passed_backend.domain.roadmap.ai.model.ValidatedRoadmapSkill;
import com.cenergy.passed_backend.domain.roadmap.entity.*;
import com.cenergy.passed_backend.domain.roadmap.skillgap.merge.CompetencyGapMergeService;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.CompetencyGapSource;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.MergedCompetencyGap;
import com.cenergy.passed_backend.domain.roadmap.skillgap.model.ValidatedSkillGapResult;
import com.cenergy.passed_backend.domain.roadmap.skillgap.validation.LearningCompetencyResponseValidator;
import com.cenergy.passed_backend.domain.skillgap.application.LearningCompetencyService;
import com.cenergy.passed_backend.domain.skillgap.dto.LearningCompetencyResponse;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoadmapGenerationServiceTest {
    private LearningCompetencyService learningCompetencyService;
    private LearningCompetencyResponseValidator validator;
    private CompetencyGapMergeService merge;
    private RoadmapAiClient ai;
    private RoadmapGenerationService service;

    @BeforeEach
    void setUp() {
        learningCompetencyService = mock(LearningCompetencyService.class);
        validator = mock(LearningCompetencyResponseValidator.class);
        merge = mock(CompetencyGapMergeService.class);
        ai = mock(RoadmapAiClient.class);
        service = new RoadmapGenerationService(learningCompetencyService, validator, merge, ai);
    }

    @Test
    void returnsGeneratedRoadmapWithoutPersistence() {
        when(learningCompetencyService.getLearningCompetencies(anyLong(), eq(1L)))
                .thenReturn(new LearningCompetencyResponse(1L, 101L, List.of()));
        when(validator.validate(eq(1L), anyLong(), any())).thenAnswer(invocation ->
                new ValidatedSkillGapResult(1L, invocation.getArgument(1), List.of()));
        when(merge.merge(anyList())).thenReturn(List.of(gap()));
        when(ai.generate(any(RoadmapAiRequest.class))).thenReturn(aiResult());

        RoadmapGenerationResult result = service.generate(1L, List.of(101L, 102L));

        assertEquals("개인 맞춤 역량 강화 로드맵", result.title());
        assertEquals("Docker", result.skills().getFirst().standardCompetencyName());
        assertEquals("Docker 실습", result.skills().getFirst().milestones().getFirst().title());
        verify(learningCompetencyService, times(2)).getLearningCompetencies(anyLong(), eq(1L));
    }

    @Test
    void doesNotCallAiWhenThereIsNoCompetencyToLearn() {
        when(learningCompetencyService.getLearningCompetencies(104L, 1L))
                .thenReturn(new LearningCompetencyResponse(1L, 104L, List.of()));
        when(validator.validate(anyLong(), anyLong(), any()))
                .thenReturn(new ValidatedSkillGapResult(1L, 104L, List.of()));
        when(merge.merge(anyList())).thenReturn(List.of());

        RoadmapException exception = assertThrows(RoadmapException.class,
                () -> service.generate(1L, List.of(104L)));

        assertEquals(ErrorCode.ROADMAP_NO_COMPETENCY_TO_LEARN, exception.getErrorCode());
        verifyNoInteractions(ai);
    }

    @Test
    void sendsOnlyTopTenMergedCompetenciesToAi() {
        when(learningCompetencyService.getLearningCompetencies(101L, 1L))
                .thenReturn(new LearningCompetencyResponse(1L, 101L, List.of()));
        when(validator.validate(anyLong(), anyLong(), any()))
                .thenReturn(new ValidatedSkillGapResult(1L, 101L, List.of()));
        List<MergedCompetencyGap> merged = IntStream.rangeClosed(1, 11)
                .mapToObj(this::gap)
                .toList();
        when(merge.merge(anyList())).thenReturn(merged);
        when(ai.generate(any(RoadmapAiRequest.class))).thenAnswer(invocation -> {
            RoadmapAiRequest request = invocation.getArgument(0);
            return new ValidatedRoadmapAiResult("top ten", request.competencies().stream()
                    .map(item -> new ValidatedRoadmapSkill(
                            item.roadmapSkillKey(),
                            List.of(milestone(item.currentLevel(), item.targetLevel()))
                    ))
                    .toList());
        });

        RoadmapGenerationResult result = service.generate(1L, List.of(101L));

        assertEquals(10, result.skills().size());
        verify(ai).generate(argThat(request -> request.competencies().size() == 10
                && request.competencies().getLast().standardCompetencyId() == 10L));
    }

    private MergedCompetencyGap gap() {
        CompetencyGapSource source = new CompetencyGapSource(101L, null, 1L, "Docker",
                CompetencyCategory.TECHNICAL_SKILL, 1, null, RequirementType.REQUIRED, 2, 1);
        return new MergedCompetencyGap("competency-1", 1L, "Docker",
                CompetencyCategory.TECHNICAL_SKILL, 1, 2, RequirementType.REQUIRED,
                1, 1, 100, 1, List.of(source));
    }

    private MergedCompetencyGap gap(int id) {
        CompetencyGapSource source = new CompetencyGapSource(101L, null, (long) id, "Skill " + id,
                CompetencyCategory.TECHNICAL_SKILL, 1, null, RequirementType.REQUIRED, 2, 1);
        return new MergedCompetencyGap("competency-" + id, (long) id, "Skill " + id,
                CompetencyCategory.TECHNICAL_SKILL, 1, 2, RequirementType.REQUIRED,
                1, 1, 100 - id, id, List.of(source));
    }

    private ValidatedRoadmapMilestone milestone(int currentLevel, int targetLevel) {
        return new ValidatedRoadmapMilestone(
                "학습", "설명", "목표", "완료 기준", currentLevel, targetLevel,
                MilestoneType.PRACTICE, Difficulty.INTERMEDIATE, 120, 1
        );
    }

    private ValidatedRoadmapAiResult aiResult() {
        ValidatedRoadmapMilestone milestone = new ValidatedRoadmapMilestone(
                "Docker 실습", "컨테이너 실습", "Docker를 사용할 수 있다", "이미지를 빌드한다",
                1, 2, MilestoneType.PRACTICE, Difficulty.INTERMEDIATE, 120, 1);
        return new ValidatedRoadmapAiResult("개인 맞춤 역량 강화 로드맵",
                List.of(new ValidatedRoadmapSkill("competency-1", List.of(milestone))));
    }
}
