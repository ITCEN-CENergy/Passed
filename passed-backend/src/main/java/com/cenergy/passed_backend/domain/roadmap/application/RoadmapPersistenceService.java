package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.*;
import com.cenergy.passed_backend.domain.roadmap.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoadmapPersistenceService {
    private final RoadmapRepository roadmapRepository;
    private final RoadmapJobPostingRepository jobPostingRepository;
    private final RoadmapSkillRepository skillRepository;
    private final RoadmapSkillSourceRepository sourceRepository;
    private final MilestoneRepository milestoneRepository;
    private final RoadmapMilestoneRepository roadmapMilestoneRepository;
    private final LearningResourceRepository resourceRepository;
    private final ResourceRecommendationRepository recommendationRepository;

    public RoadmapPersistenceService(RoadmapRepository roadmapRepository,
                                     RoadmapJobPostingRepository jobPostingRepository,
                                     RoadmapSkillRepository skillRepository,
                                     RoadmapSkillSourceRepository sourceRepository,
                                     MilestoneRepository milestoneRepository,
                                     RoadmapMilestoneRepository roadmapMilestoneRepository,
                                     LearningResourceRepository resourceRepository,
                                     ResourceRecommendationRepository recommendationRepository) {
        this.roadmapRepository = roadmapRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.skillRepository = skillRepository;
        this.sourceRepository = sourceRepository;
        this.milestoneRepository = milestoneRepository;
        this.roadmapMilestoneRepository = roadmapMilestoneRepository;
        this.resourceRepository = resourceRepository;
        this.recommendationRepository = recommendationRepository;
    }

    @Transactional
    public Roadmap save(Long userId, List<Long> jobPostingIds, RoadmapGenerationResult result) {
        Roadmap roadmap = roadmapRepository.save(Roadmap.create(userId));
        jobPostingRepository.saveAll(jobPostingIds.stream()
                .map(id -> RoadmapJobPosting.create(roadmap, id, null)).toList());

        int totalMinutes = 0;
        for (RoadmapGenerationResult.Skill generatedSkill : result.skills()) {
            int skillMinutes = generatedSkill.milestones().stream()
                    .mapToInt(RoadmapGenerationResult.Milestone::estimatedMinutes).sum();
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

            for (RoadmapGenerationResult.Milestone generatedMilestone : generatedSkill.milestones()) {
                Milestone milestone = milestoneRepository.save(Milestone.create(
                        userId, generatedSkill.standardCompetencyId(), generatedMilestone.title(),
                        generatedMilestone.description(), generatedMilestone.learningObjective(),
                        generatedMilestone.completionCriteria(), generatedMilestone.startLevel(),
                        generatedMilestone.targetLevel(), generatedMilestone.milestoneType(),
                        generatedMilestone.difficulty(), generatedMilestone.estimatedMinutes()));
                roadmapMilestoneRepository.save(RoadmapMilestone.create(
                        skill, milestone, generatedMilestone.learningOrder()));

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
            totalMinutes += skillMinutes;
        }
        roadmap.activate(result.title(), totalMinutes);
        return roadmap;
    }
}
