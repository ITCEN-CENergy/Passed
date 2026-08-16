package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.entity.MilestoneStatus;
import com.cenergy.passed_backend.domain.roadmap.entity.Roadmap;
import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapMilestone;
import com.cenergy.passed_backend.domain.roadmap.entity.RoadmapSkill;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapMilestoneRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapRepository;
import com.cenergy.passed_backend.domain.roadmap.repository.RoadmapSkillRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RoadmapProgressSynchronizer {
    private final RoadmapMilestoneRepository roadmapMilestoneRepository;
    private final RoadmapSkillRepository roadmapSkillRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapEtaCalculator etaCalculator;

    public RoadmapProgressSynchronizer(RoadmapMilestoneRepository roadmapMilestoneRepository,
                                       RoadmapSkillRepository roadmapSkillRepository,
                                       RoadmapRepository roadmapRepository,
                                       RoadmapEtaCalculator etaCalculator) {
        this.roadmapMilestoneRepository = roadmapMilestoneRepository;
        this.roadmapSkillRepository = roadmapSkillRepository;
        this.roadmapRepository = roadmapRepository;
        this.etaCalculator = etaCalculator;
    }

    public void synchronizeByMilestone(Long milestoneId) {
        List<Long> affectedSkillIds = roadmapMilestoneRepository
                .findRoadmapSkillIdsByMilestoneId(milestoneId);
        if (affectedSkillIds.isEmpty()) return;

        Map<Long, RoadmapSkill> skillsById = roadmapSkillRepository.findAllById(affectedSkillIds).stream()
                .collect(Collectors.toMap(RoadmapSkill::getId, Function.identity()));
        Map<Long, List<RoadmapMilestone>> linksBySkill = roadmapMilestoneRepository
                .findAllByRoadmapSkillIds(affectedSkillIds).stream()
                .collect(Collectors.groupingBy(link -> link.getRoadmapSkill().getId()));

        Set<Long> affectedRoadmapIds = new LinkedHashSet<>();
        for (Long skillId : affectedSkillIds) {
            RoadmapSkill skill = skillsById.get(skillId);
            if (skill == null) continue;
            skill.updateProgressRate(calculate(linksBySkill.getOrDefault(skillId, List.of())));
            affectedRoadmapIds.add(skill.getRoadmap().getId());
        }

        for (Long roadmapId : affectedRoadmapIds) {
            Roadmap roadmap = roadmapRepository.findById(roadmapId).orElse(null);
            if (roadmap == null) continue;
            List<RoadmapSkill> roadmapSkills = roadmapSkillRepository
                    .findAllByRoadmapIdOrderByPriorityAscIdAsc(roadmapId);
            List<Long> roadmapSkillIds = roadmapSkills.stream().map(RoadmapSkill::getId).toList();
            List<RoadmapMilestone> roadmapLinks = roadmapSkillIds.isEmpty() ? List.of()
                    : roadmapMilestoneRepository.findAllByRoadmapSkillIds(roadmapSkillIds);
            roadmap.updateProgressRate(calculate(roadmapLinks));
            roadmap.updateEstimatedEndDate(etaCalculator.calculate(
                    roadmapLinks, roadmap.getDailyStudyMinutes()));
        }
    }

    public void synchronizeRoadmap(Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId).orElse(null);
        if (roadmap == null) return;
        List<RoadmapSkill> skills = roadmapSkillRepository
                .findAllByRoadmapIdOrderByPriorityAscIdAsc(roadmapId);
        List<Long> skillIds = skills.stream().map(RoadmapSkill::getId).toList();
        List<RoadmapMilestone> links = skillIds.isEmpty() ? List.of()
                : roadmapMilestoneRepository.findAllByRoadmapSkillIds(skillIds);
        Map<Long, List<RoadmapMilestone>> bySkill = links.stream()
                .collect(Collectors.groupingBy(link -> link.getRoadmapSkill().getId()));
        for (RoadmapSkill skill : skills) {
            List<RoadmapMilestone> skillLinks = bySkill.getOrDefault(skill.getId(), List.of());
            skill.updateProgressRate(calculate(skillLinks));
            skill.updateEstimatedMinutes(skillLinks.stream()
                    .filter(RoadmapMilestone::isRequired)
                    .mapToInt(link -> link.getMilestone().getEstimatedMinutes()).sum());
        }
        roadmap.updateTotalEstimatedMinutes(links.stream()
                .filter(RoadmapMilestone::isRequired)
                .mapToInt(link -> link.getMilestone().getEstimatedMinutes()).sum());
        roadmap.updateProgressRate(calculate(links));
        roadmap.updateEstimatedEndDate(etaCalculator.calculate(
                links, roadmap.getDailyStudyMinutes()));
    }

    public void synchronizeInitialProgress(Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId).orElse(null);
        if (roadmap == null) return;
        List<RoadmapSkill> skills = roadmapSkillRepository
                .findAllByRoadmapIdOrderByPriorityAscIdAsc(roadmapId);
        List<Long> skillIds = skills.stream().map(RoadmapSkill::getId).toList();
        List<RoadmapMilestone> links = skillIds.isEmpty() ? List.of()
                : roadmapMilestoneRepository.findAllByRoadmapSkillIds(skillIds);
        Map<Long, List<RoadmapMilestone>> bySkill = links.stream()
                .collect(Collectors.groupingBy(link -> link.getRoadmapSkill().getId()));
        for (RoadmapSkill skill : skills) {
            skill.updateProgressRate(calculate(bySkill.getOrDefault(skill.getId(), List.of())));
        }
        roadmap.updateProgressRate(calculate(links));
        roadmap.updateEstimatedEndDate(etaCalculator.calculate(
                links, roadmap.getDailyStudyMinutes()));
    }

    private BigDecimal calculate(Collection<RoadmapMilestone> links) {
        List<RoadmapMilestone> required = links.stream()
                .filter(RoadmapMilestone::isRequired)
                .toList();
        if (required.isEmpty()) return BigDecimal.ZERO.setScale(2);
        long completed = required.stream()
                .filter(link -> link.getMilestone().getStatus() == MilestoneStatus.COMPLETED)
                .count();
        return BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(required.size()), 2, RoundingMode.HALF_UP);
    }
}
