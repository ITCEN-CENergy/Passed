package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.*;
import com.cenergy.passed_backend.domain.roadmap.repository.*;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class RoadmapPersistenceService {
    private final RoadmapRepository roadmapRepository;
    private final RoadmapSkillRepository skillRepository;
    private final RoadmapSkillSourceRepository sourceRepository;
    private final MilestoneRepository milestoneRepository;
    private final RoadmapMilestoneRepository roadmapMilestoneRepository;
    private final LearningResourceRepository resourceRepository;
    private final ResourceRecommendationRepository recommendationRepository;
    private final MilestoneReuseService milestoneReuseService;
    private final RoadmapEtaCalculator etaCalculator;

    public RoadmapPersistenceService(RoadmapRepository roadmapRepository,
                                     RoadmapSkillRepository skillRepository,
                                     RoadmapSkillSourceRepository sourceRepository,
                                     MilestoneRepository milestoneRepository,
                                     RoadmapMilestoneRepository roadmapMilestoneRepository,
                                     LearningResourceRepository resourceRepository,
                                     ResourceRecommendationRepository recommendationRepository,
                                     MilestoneReuseService milestoneReuseService,
                                     RoadmapEtaCalculator etaCalculator) {
        this.roadmapRepository = roadmapRepository;
        this.skillRepository = skillRepository;
        this.sourceRepository = sourceRepository;
        this.milestoneRepository = milestoneRepository;
        this.roadmapMilestoneRepository = roadmapMilestoneRepository;
        this.resourceRepository = resourceRepository;
        this.recommendationRepository = recommendationRepository;
        this.milestoneReuseService = milestoneReuseService;
        this.etaCalculator = etaCalculator;
    }

    @Transactional
    public Roadmap complete(Long roadmapId, Long userId, RoadmapGenerationResult result) {
        List<Long> competencyIds = result.skills().stream()
                .map(RoadmapGenerationResult.Skill::standardCompetencyId)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new), List::copyOf));
        List<Milestone> reusableMilestones = milestoneReuseService.findCandidates(userId, competencyIds);

        Roadmap roadmap = roadmapRepository.findByIdAndUserId(roadmapId, userId)
                .orElseThrow(() -> new RoadmapException(
                        ErrorCode.ROADMAP_NOT_FOUND,
                        "Roadmap not found"));
        if (roadmap.getStatus() != RoadmapStatus.CREATING) {
            throw new RoadmapException(
                    ErrorCode.ROADMAP_GENERATION_CONFLICT,
                    "Roadmap is not in CREATING status",
                    roadmapId);
        }

        int totalMinutes = 0;
        List<RoadmapMilestone> savedRoadmapMilestones = new java.util.ArrayList<>();
        for (RoadmapGenerationResult.Skill generatedSkill : result.skills()) {
            List<MilestoneReuseDecision> reuseDecisions = new java.util.ArrayList<>();
            Set<Long> assignedMilestoneIds = new java.util.HashSet<>();
            for (RoadmapGenerationResult.Milestone candidate : generatedSkill.milestones()) {
                MilestoneReuseDecision decision = milestoneReuseService.decide(
                        generatedSkill, candidate, reusableMilestones, assignedMilestoneIds);
                reuseDecisions.add(decision);
                if (decision.milestone() != null) {
                    assignedMilestoneIds.add(decision.milestone().getId());
                }
            }
            int skillMinutes = IntStream.range(0, generatedSkill.milestones().size())
                    .map(index -> {
                        Milestone reused = reuseDecisions.get(index).milestone();
                        return reused == null
                                ? generatedSkill.milestones().get(index).estimatedMinutes()
                                : reused.getEstimatedMinutes();
                    })
                    .sum();
            RoadmapSkill skill = skillRepository.save(RoadmapSkill.create(
                    roadmap, generatedSkill.standardCompetencyId(), generatedSkill.standardCompetencyName(),
                    generatedSkill.category(), generatedSkill.currentLevel(), generatedSkill.targetLevel(),
                    generatedSkill.requirementType(), generatedSkill.gapLevel(), generatedSkill.frequency(),
                    generatedSkill.priorityScore(), generatedSkill.priority(), skillMinutes));

            sourceRepository.saveAll(generatedSkill.sources().stream().map(source -> RoadmapSkillSource.create(
                    skill, source.jobPostingId(), source.reportId(), source.standardCompetencyId(),
                    source.standardCompetencyName(), source.category(), source.currentLevel(),
                    source.currentEvidence(), source.requirementType(), source.targetLevel(), source.gapLevel()
            )).toList());

            for (int index = 0; index < generatedSkill.milestones().size(); index++) {
                RoadmapGenerationResult.Milestone generatedMilestone = generatedSkill.milestones().get(index);
                MilestoneReuseDecision reuse = reuseDecisions.get(index);
                Milestone milestone = reuse.milestone();
                if (milestone == null) {
                    milestone = milestoneRepository.save(Milestone.create(
                            userId, generatedSkill.standardCompetencyId(), generatedMilestone.title(),
                            generatedMilestone.description(), generatedMilestone.learningObjective(),
                            generatedMilestone.completionCriteria(), generatedMilestone.startLevel(),
                            generatedMilestone.targetLevel(), generatedMilestone.milestoneType(),
                            generatedMilestone.difficulty(), generatedMilestone.estimatedMinutes()));
                }
                RoadmapMilestone roadmapMilestone = roadmapMilestoneRepository.save(RoadmapMilestone.create(
                        skill, milestone, generatedMilestone.learningOrder(), reuse.reuseType(), reuse.reason(),
                        generatedMilestone.required()));
                savedRoadmapMilestones.add(roadmapMilestone);

                if (reuse.reuseType() == ReuseType.NEW) {
                    int rank = 1;
                    for (RoadmapGenerationResult.LearningResource generatedResource
                            : generatedMilestone.learningResources()) {
                        LearningResource resource = resourceRepository.save(LearningResource.create(
                                generatedResource.provider(), generatedResource.resourceId(),
                                generatedResource.resourceType(), generatedResource.title(),
                                generatedResource.description(), generatedResource.url(),
                                generatedResource.thumbnailUrl()));
                        recommendationRepository.save(ResourceRecommendation.create(milestone, resource, rank++));
                    }
                }
            }
            totalMinutes += skillMinutes;
        }
        roadmap.activate(result.title(), totalMinutes);
        roadmap.initializeEndDate(etaCalculator.calculate(savedRoadmapMilestones));
        return roadmap;
    }
}
