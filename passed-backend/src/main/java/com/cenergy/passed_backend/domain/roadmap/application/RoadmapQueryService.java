package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.api.RoadmapDetailResponse;
import com.cenergy.passed_backend.domain.roadmap.api.RoadmapListResponse;
import com.cenergy.passed_backend.domain.roadmap.entity.*;
import com.cenergy.passed_backend.domain.roadmap.repository.*;
import com.cenergy.passed_backend.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RoadmapQueryService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapJobPostingRepository jobPostingRepository;
    private final RoadmapSkillRepository skillRepository;
    private final RoadmapSkillSourceRepository sourceRepository;
    private final RoadmapMilestoneRepository roadmapMilestoneRepository;
    private final ResourceRecommendationRepository recommendationRepository;

    public RoadmapQueryService(CurrentUserIdProvider currentUserIdProvider,
                               RoadmapRepository roadmapRepository,
                               RoadmapJobPostingRepository jobPostingRepository,
                               RoadmapSkillRepository skillRepository,
                               RoadmapSkillSourceRepository sourceRepository,
                               RoadmapMilestoneRepository roadmapMilestoneRepository,
                               ResourceRecommendationRepository recommendationRepository) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.roadmapRepository = roadmapRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.skillRepository = skillRepository;
        this.sourceRepository = sourceRepository;
        this.roadmapMilestoneRepository = roadmapMilestoneRepository;
        this.recommendationRepository = recommendationRepository;
    }

    public RoadmapListResponse findAll() {
        List<Roadmap> roadmaps = roadmapRepository
                .findAllByUserIdOrderByCreatedAtDescIdDesc(currentUserId());
        List<Long> ids = roadmaps.stream().map(Roadmap::getId).toList();
        Map<Long, Long> postingCounts = counts(ids, jobPostingRepository::countByRoadmapIds);
        Map<Long, Long> skillCounts = counts(ids, skillRepository::countByRoadmapIds);
        Map<Long, Long> milestoneCounts = counts(ids, roadmapMilestoneRepository::countByRoadmapIds);
        return new RoadmapListResponse(roadmaps.stream().map(roadmap -> new RoadmapListResponse.Item(
                roadmap.getId(), roadmap.getTitle(), roadmap.getStatus(), roadmap.getTotalEstimatedMinutes(),
                roadmap.getProgressRate(), count(postingCounts, roadmap.getId()), count(skillCounts, roadmap.getId()),
                count(milestoneCounts, roadmap.getId()), roadmap.getCreatedAt(), roadmap.getUpdatedAt()
        )).toList());
    }

    public RoadmapDetailResponse findById(Long roadmapId) {
        if (roadmapId == null || roadmapId <= 0) {
            throw new RoadmapException(ErrorCode.ROADMAP_INVALID_REQUEST, "Invalid roadmapId");
        }
        Roadmap roadmap = roadmapRepository.findByIdAndUserId(roadmapId, currentUserId())
                .orElseThrow(() -> new RoadmapException(ErrorCode.ROADMAP_NOT_FOUND, "Roadmap not found"));
        List<RoadmapJobPosting> postings = jobPostingRepository.findAllByRoadmapIdOrderByIdAsc(roadmapId);
        List<RoadmapSkill> skills = skillRepository.findAllByRoadmapIdOrderByPriorityAscIdAsc(roadmapId);
        List<Long> skillIds = skills.stream().map(RoadmapSkill::getId).toList();
        List<RoadmapSkillSource> sources = skillIds.isEmpty() ? List.of()
                : sourceRepository.findAllByRoadmapSkillIdInOrderByRoadmapSkillIdAscIdAsc(skillIds);
        Map<Long, List<RoadmapSkillSource>> sourcesBySkill = sources.stream()
                .collect(Collectors.groupingBy(value -> value.getRoadmapSkill().getId(), LinkedHashMap::new, Collectors.toList()));
        List<RoadmapMilestone> roadmapMilestones = skillIds.isEmpty() ? List.of()
                : roadmapMilestoneRepository.findAllByRoadmapSkillIds(skillIds);
        Map<Long, List<RoadmapMilestone>> milestonesBySkill = roadmapMilestones.stream()
                .collect(Collectors.groupingBy(value -> value.getRoadmapSkill().getId(), LinkedHashMap::new, Collectors.toList()));
        List<Long> milestoneIds = milestonesBySkill.values().stream().flatMap(Collection::stream)
                .map(value -> value.getMilestone().getId()).toList();
        List<ResourceRecommendation> recommendations = milestoneIds.isEmpty() ? List.of()
                : recommendationRepository.findAllByMilestoneIdInOrderByMilestoneIdAscRankOrderAsc(milestoneIds);
        Map<Long, List<ResourceRecommendation>> resourcesByMilestone = recommendations.stream()
                .collect(Collectors.groupingBy(value -> value.getMilestone().getId(), LinkedHashMap::new, Collectors.toList()));

        return new RoadmapDetailResponse(roadmap.getId(), roadmap.getTitle(), roadmap.getStatus(),
                roadmap.getTotalEstimatedMinutes(), roadmap.getProgressRate(), roadmap.getEstimatedEndDate(),
                roadmap.getFailureReason(), postings.stream().map(RoadmapJobPosting::getJobPostingId).toList(),
                skills.stream().map(skill -> toSkill(skill, sourcesBySkill.getOrDefault(skill.getId(), List.of()),
                        milestonesBySkill.getOrDefault(skill.getId(), List.of()), resourcesByMilestone)).toList(),
                roadmap.getCreatedAt(), roadmap.getUpdatedAt());
    }

    private RoadmapDetailResponse.Skill toSkill(RoadmapSkill skill, List<RoadmapSkillSource> sources,
                                                List<RoadmapMilestone> milestones,
                                                Map<Long, List<ResourceRecommendation>> resources) {
        return new RoadmapDetailResponse.Skill(skill.getId(), skill.getStandardCompetencyId(),
                skill.getStandardCompetencyName(), skill.getCategory(), skill.getCurrentLevel(), skill.getTargetLevel(),
                skill.getRequirementType(), skill.getGapLevel(), skill.getFrequency(), skill.getPriorityScore(),
                skill.getPriority(), skill.getEstimatedMinutes(), skill.getProgressRate(),
                sources.stream().map(value -> new RoadmapDetailResponse.Source(value.getJobPostingId(),
                        value.getReportId(), value.getCurrentLevel(), value.getCurrentEvidence(),
                        value.getRequirementType(), value.getTargetLevel(), value.getGapLevel())).toList(),
                milestones.stream().map(value -> toMilestone(value,
                        resources.getOrDefault(value.getMilestone().getId(), List.of()))).toList());
    }

    private RoadmapDetailResponse.MilestoneItem toMilestone(RoadmapMilestone link,
                                                             List<ResourceRecommendation> recommendations) {
        Milestone value = link.getMilestone();
        return new RoadmapDetailResponse.MilestoneItem(value.getId(), value.getTitle(), value.getDescription(),
                value.getLearningObjective(), value.getCompletionCriteria(), value.getStartLevel(), value.getTargetLevel(),
                value.getMilestoneType(), value.getDifficulty(), value.getEstimatedMinutes(), link.getLearningOrder(),
                value.getStatus(), value.getProgressRate(), link.getReuseType(), link.getReuseReason(), link.isRequired(),
                recommendations.stream().map(recommendation -> {
                    LearningResource resource = recommendation.getResource();
                    return new RoadmapDetailResponse.Resource(resource.getId(), resource.getExternalId(),
                            resource.getResourceType(), resource.getTitle(), resource.getDescription(),
                            resource.getProvider(), resource.getUrl(), resource.getThumbnailUrl(),
                            recommendation.getRankOrder());
                }).toList());
    }

    private Long currentUserId() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new RoadmapException(ErrorCode.ROADMAP_INVALID_REQUEST, "Invalid current user");
        }
        return userId;
    }

    private Map<Long, Long> counts(List<Long> ids, Function<Collection<Long>, List<RoadmapCount>> loader) {
        if (ids.isEmpty()) return Map.of();
        return loader.apply(ids).stream().collect(Collectors.toMap(RoadmapCount::getRoadmapId, RoadmapCount::getCount));
    }

    private int count(Map<Long, Long> counts, Long id) {
        return Math.toIntExact(counts.getOrDefault(id, 0L));
    }
}
