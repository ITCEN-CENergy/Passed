package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.Milestone;
import com.cenergy.passed_backend.domain.roadmap.repository.MilestoneRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.List;

@Service
public class MilestoneReuseService {
    private final MilestoneRepository milestoneRepository;

    public MilestoneReuseService(MilestoneRepository milestoneRepository) {
        this.milestoneRepository = milestoneRepository;
    }

    public List<Milestone> findCandidates(Long userId, Collection<Long> standardCompetencyIds) {
        if (standardCompetencyIds.isEmpty()) {
            return List.of();
        }
        return milestoneRepository.findAllByUserIdAndStandardCompetencyIdInOrderByIdAsc(
                userId, standardCompetencyIds);
    }

    public MilestoneReuseDecision decide(RoadmapGenerationResult.Skill skill,
                                         RoadmapGenerationResult.Milestone candidate,
                                         Collection<Milestone> existingMilestones) {
        return decide(skill, candidate, existingMilestones, Set.of());
    }

    public MilestoneReuseDecision decide(RoadmapGenerationResult.Skill skill,
                                         RoadmapGenerationResult.Milestone candidate,
                                         Collection<Milestone> existingMilestones,
                                         Set<Long> alreadyAssignedMilestoneIds) {
        return existingMilestones.stream()
                .filter(existing -> existing.getStandardCompetencyId().equals(skill.standardCompetencyId()))
                .filter(existing -> existing.getId() == null
                        || !alreadyAssignedMilestoneIds.contains(existing.getId()))
                .filter(existing -> existing.getMilestoneType() == candidate.milestoneType())
                .filter(existing -> existing.getStartLevel() <= candidate.startLevel())
                .filter(existing -> existing.getTargetLevel() >= candidate.targetLevel())
                .min(Comparator
                        .comparingInt((Milestone existing) ->
                                existing.getTargetLevel() - existing.getStartLevel())
                        .thenComparing(Milestone::getId, Comparator.nullsLast(Long::compareTo)))
                .map(MilestoneReuseDecision::reused)
                .orElseGet(MilestoneReuseDecision::newMilestone);
    }
}
