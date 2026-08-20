package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.ai.client.RoadmapAiClient;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapReplanAiRequest;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapReplanAiResponse;
import com.cenergy.passed_backend.domain.roadmap.dto.RoadmapReplanApplyRequest;
import com.cenergy.passed_backend.domain.roadmap.dto.RoadmapReplanApplyResponse;
import com.cenergy.passed_backend.domain.roadmap.dto.RoadmapReplanPreviewRequest;
import com.cenergy.passed_backend.domain.roadmap.dto.RoadmapReplanPreviewResponse;
import com.cenergy.passed_backend.domain.roadmap.entity.*;
import com.cenergy.passed_backend.domain.roadmap.repository.*;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.cenergy.passed_backend.global.security.CurrentUserIdProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RoadmapReplanService {
    private static final int REDUCTION_TOLERANCE_MINUTES = 60;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapSkillRepository skillRepository;
    private final RoadmapMilestoneRepository linkRepository;
    private final MilestoneRepository milestoneRepository;
    private final LearningResourceRepository resourceRepository;
    private final ResourceRecommendationRepository recommendationRepository;
    private final RoadmapReplanRepository replanRepository;
    private final RoadmapAiClient aiClient;
    private final RoadmapEtaCalculator etaCalculator;
    private final RoadmapProgressSynchronizer progressSynchronizer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RoadmapReplanService(CurrentUserIdProvider currentUserIdProvider,
                                RoadmapRepository roadmapRepository,
                                RoadmapSkillRepository skillRepository,
                                RoadmapMilestoneRepository linkRepository,
                                MilestoneRepository milestoneRepository,
                                LearningResourceRepository resourceRepository,
                                ResourceRecommendationRepository recommendationRepository,
                                RoadmapReplanRepository replanRepository,
                                RoadmapAiClient aiClient,
                                RoadmapEtaCalculator etaCalculator,
                                RoadmapProgressSynchronizer progressSynchronizer) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.roadmapRepository = roadmapRepository;
        this.skillRepository = skillRepository;
        this.linkRepository = linkRepository;
        this.milestoneRepository = milestoneRepository;
        this.resourceRepository = resourceRepository;
        this.recommendationRepository = recommendationRepository;
        this.replanRepository = replanRepository;
        this.aiClient = aiClient;
        this.etaCalculator = etaCalculator;
        this.progressSynchronizer = progressSynchronizer;
    }

    @Transactional
    public RoadmapReplanPreviewResponse preview(Long roadmapId, RoadmapReplanPreviewRequest request) {
        Long userId = currentUserId();
        Roadmap roadmap = activeRoadmap(roadmapId, userId);
        List<RoadmapMilestone> allLinks = links(roadmapId);
        RoadmapScheduleAssessment schedule = RoadmapScheduleAssessment.assess(
                roadmap.getBaselineEndDate(),
                etaCalculator.calculate(allLinks, roadmap.getDailyStudyMinutes()));
        if (schedule.status() != RoadmapScheduleStatus.DELAYED) {
            throw invalid("Only delayed roadmaps can be replanned");
        }
        List<RoadmapMilestone> candidates = candidates(allLinks);
        if (candidates.isEmpty()) throw invalid("No not-started milestones are available for compression");

        List<GroupDraft> groups = group(candidates);
        int candidateMinutes = totalMinutes(candidates);
        int requestedReduction = Math.toIntExact(Math.min((long) candidateMinutes,
                schedule.delayDays() * roadmap.getDailyStudyMinutes()));
        int capacity = groups.stream().mapToInt(value -> Math.max(0, value.originalMinutes() - 30)).sum();
        int maximumReduction = Math.min(capacity, requestedReduction + REDUCTION_TOLERANCE_MINUTES);
        int targetReduction = Math.min(requestedReduction, maximumReduction);
        allocateMinutes(groups, targetReduction);

        RoadmapReplanAiRequest aiRequest = new RoadmapReplanAiRequest(
                roadmapId, roadmap.getTitle(), instruction(request), groups.stream().map(this::toAiGroup).toList());
        RoadmapReplanAiResponse aiResponse = aiClient.replan(aiRequest);
        Map<String, RoadmapReplanAiResponse.CompressedGroup> generated = validate(groups, aiResponse);
        RoadmapCompressionPlan plan = bind(groups, aiResponse.summary(), generated, allLinks);
        RoadmapReplan replan = replanRepository.save(RoadmapReplan.ready(
                roadmapId, userId, plan.summary(), objectMapper.valueToTree(plan)));

        int previousRemaining = remainingMinutes(allLinks);
        int protectedRemaining = remainingMinutes(allLinks.stream()
                .filter(link -> !candidates.contains(link)).toList());
        int replannedRemaining = protectedRemaining + plan.groups().stream()
                .mapToInt(RoadmapCompressionPlan.Group::assignedEstimatedMinutes).sum();
        return new RoadmapReplanPreviewResponse(
                roadmapId, replan.getToken(), plan.summary(), previousRemaining, replannedRemaining,
                etaCalculator.calculateRemainingMinutes(previousRemaining, roadmap.getDailyStudyMinutes()),
                etaCalculator.calculateRemainingMinutes(replannedRemaining, roadmap.getDailyStudyMinutes()),
                previewSkills(plan));
    }

    @Transactional
    public RoadmapReplanApplyResponse apply(Long roadmapId, RoadmapReplanApplyRequest request) {
        Long userId = currentUserId();
        Roadmap roadmap = activeRoadmapForUpdate(roadmapId, userId);
        RoadmapReplan replan = replanRepository.findOwnedForUpdate(request.replanToken(), roadmapId, userId)
                .orElseThrow(() -> invalid("Replan token is invalid for this roadmap"));
        if (replan.getStatus() == RoadmapReplanStatus.APPLIED) {
            return response(roadmap);
        }
        RoadmapCompressionPlan plan = read(replan.getDecisionsJson());
        List<RoadmapMilestone> allLinks = links(roadmapId);
        if (!snapshot(allLinks).equals(plan.sourceSnapshot())) {
            throw invalid("Roadmap changed after compression preview");
        }
        List<Long> milestoneIds = allLinks.stream().map(link -> link.getMilestone().getId())
                .distinct().sorted().toList();
        if (!milestoneIds.isEmpty()) milestoneRepository.findAllForUpdateByIdInOrderById(milestoneIds);
        List<RoadmapMilestone> candidates = candidates(allLinks);
        Set<Long> currentIds = candidates.stream().map(link -> link.getMilestone().getId()).collect(Collectors.toSet());
        Set<Long> plannedIds = plan.groups().stream().flatMap(group -> group.sourceMilestoneIds().stream())
                .collect(Collectors.toSet());
        long plannedOccurrences = plan.groups().stream().mapToLong(group -> group.sourceMilestoneIds().size()).sum();
        if (!currentIds.equals(plannedIds) || plannedOccurrences != plannedIds.size()) {
            throw invalid("Roadmap changed after compression preview");
        }

        Map<Long, RoadmapSkill> skills = skillRepository.findAllByRoadmapIdOrderByPriorityAscIdAsc(roadmapId)
                .stream().collect(Collectors.toMap(RoadmapSkill::getId, Function.identity()));
        List<RoadmapMilestone> protectedLinks = allLinks.stream()
                .filter(link -> !currentIds.contains(link.getMilestone().getId())).toList();
        linkRepository.deleteAll(candidates);
        linkRepository.flush();
        for (int index = 0; index < protectedLinks.size(); index++) {
            protectedLinks.get(index).reorder(1_000_000 + index);
        }
        linkRepository.flush();
        Map<Long, List<RoadmapMilestone>> protectedBySkill = protectedLinks.stream()
                .collect(Collectors.groupingBy(link -> link.getRoadmapSkill().getId()));
        for (List<RoadmapMilestone> values : protectedBySkill.values()) {
            for (int index = 0; index < values.size(); index++) values.get(index).reorder(index + 1);
        }
        linkRepository.flush();

        for (RoadmapCompressionPlan.Group item : plan.groups()) {
            RoadmapSkill skill = skills.get(item.roadmapSkillId());
            if (skill == null) throw invalid("Compressed skill no longer exists");
            Milestone milestone = milestoneRepository.save(Milestone.create(
                    userId, skill.getStandardCompetencyId(), item.title(), item.description(),
                    item.learningObjective(), item.completionCriteria(), item.startLevel(), item.targetLevel(),
                    item.milestoneType(), item.difficulty(), item.assignedEstimatedMinutes()));
            int resourceRank = 1;
            for (RoadmapCompressionPlan.Resource itemResource : item.learningResources()) {
                LearningResource resource = resourceRepository.save(LearningResource.create(
                        itemResource.provider(), itemResource.externalId(), itemResource.resourceType(),
                        itemResource.title(), itemResource.description(), itemResource.url(),
                        itemResource.thumbnailUrl()));
                recommendationRepository.save(ResourceRecommendation.create(
                        milestone, resource, resourceRank++));
            }
            int offset = protectedBySkill.getOrDefault(item.roadmapSkillId(), List.of()).size();
            linkRepository.save(RoadmapMilestone.create(
                    skill, milestone, offset + item.learningOrder(), ReuseType.NEW,
                    "Compressed from milestones " + item.sourceMilestoneIds()));
        }
        linkRepository.flush();
        replan.markApplied(OffsetDateTime.now());
        progressSynchronizer.synchronizeRoadmap(roadmapId);
        return response(roadmap);
    }

    private List<GroupDraft> group(List<RoadmapMilestone> candidates) {
        Map<Long, List<RoadmapMilestone>> bySkill = candidates.stream().collect(Collectors.groupingBy(
                link -> link.getRoadmapSkill().getId(), LinkedHashMap::new, Collectors.toList()));
        List<GroupDraft> result = new ArrayList<>();
        for (Map.Entry<Long, List<RoadmapMilestone>> entry : bySkill.entrySet()) {
            List<RoadmapMilestone> values = entry.getValue();
            int groupOrder = 1;
            for (int index = 0; index < values.size(); ) {
                List<RoadmapMilestone> sources = new ArrayList<>();
                RoadmapMilestone first = values.get(index++);
                sources.add(first);
                if (index < values.size() && mergeable(first, values.get(index))) {
                    sources.add(values.get(index++));
                }
                result.add(new GroupDraft("skill-" + entry.getKey() + "-group-" + groupOrder,
                        entry.getKey(), first.getRoadmapSkill().getStandardCompetencyName(), groupOrder++,
                        sources, totalMinutes(sources), totalMinutes(sources)));
            }
        }
        return result;
    }

    private boolean mergeable(RoadmapMilestone left, RoadmapMilestone right) {
        Set<MilestoneType> standalone = Set.of(
                MilestoneType.PROJECT, MilestoneType.ASSESSMENT, MilestoneType.CERTIFICATION);
        return !standalone.contains(left.getMilestone().getMilestoneType())
                && !standalone.contains(right.getMilestone().getMilestoneType())
                && left.getMilestone().getTargetLevel().equals(right.getMilestone().getTargetLevel());
    }

    private void allocateMinutes(List<GroupDraft> groups, int targetReduction) {
        int remainingReduction = targetReduction;
        int remainingCapacity = groups.stream()
                .mapToInt(value -> Math.max(0, value.originalMinutes() - 30)).sum();
        for (int index = 0; index < groups.size(); index++) {
            GroupDraft group = groups.get(index);
            int capacity = Math.max(0, group.originalMinutes() - 30);
            int reduction = index == groups.size() - 1 ? remainingReduction
                    : remainingCapacity == 0 ? 0
                      : Math.min(capacity, (int) ((long) remainingReduction * capacity / remainingCapacity));
            group.assignMinutes(group.originalMinutes() - reduction);
            remainingReduction -= reduction;
            remainingCapacity -= capacity;
        }
        if (remainingReduction != 0) throw invalid("Unable to allocate compression time");
    }

    private RoadmapReplanAiRequest.Group toAiGroup(GroupDraft group) {
        return new RoadmapReplanAiRequest.Group(
                group.groupKey(), group.roadmapSkillId(), group.skill().getStandardCompetencyId(),
                group.skillName(), group.skill().getCategory(), group.skill().getCurrentLevel(),
                group.skill().getTargetLevel(), group.assignedMinutes(),
                group.sources().stream().map(link -> {
                    Milestone item = link.getMilestone();
                    return new RoadmapReplanAiRequest.SourceMilestone(
                            item.getTitle(), item.getDescription() == null ? item.getTitle() : item.getDescription(),
                            item.getLearningObjective(), item.getCompletionCriteria(), item.getStartLevel(),
                            item.getTargetLevel(), item.getMilestoneType(), item.getDifficulty(),
                            item.getEstimatedMinutes());
                }).toList());
    }

    private Map<String, RoadmapReplanAiResponse.CompressedGroup> validate(
            List<GroupDraft> groups, RoadmapReplanAiResponse response) {
        if (response == null || blank(response.summary()) || response.groups() == null) {
            throw invalid("Invalid compression response");
        }
        Set<String> expected = groups.stream().map(GroupDraft::groupKey).collect(Collectors.toSet());
        Map<String, RoadmapReplanAiResponse.CompressedGroup> generated = new LinkedHashMap<>();
        for (RoadmapReplanAiResponse.CompressedGroup item : response.groups()) {
            if (item == null || !expected.contains(item.groupKey())
                    || generated.putIfAbsent(item.groupKey(), item) != null
                    || blank(item.title()) || blank(item.description()) || blank(item.learningObjective())
                    || blank(item.completionCriteria()) || blank(item.compressionReason())
                    || item.milestoneType() == null || item.difficulty() == null
                    || item.learningResources().size() > 3
                    || item.learningResources().stream().anyMatch(resource -> resource == null
                    || blank(resource.resourceId()) || blank(resource.resourceType())
                    || blank(resource.title()) || blank(resource.provider()) || blank(resource.url()))) {
                throw invalid("Invalid compressed group content");
            }
        }
        if (!generated.keySet().equals(expected)) throw invalid("Compression response is incomplete");
        return generated;
    }

    private RoadmapCompressionPlan bind(List<GroupDraft> groups, String summary,
                                        Map<String, RoadmapReplanAiResponse.CompressedGroup> generated,
                                        List<RoadmapMilestone> allLinks) {
        return new RoadmapCompressionPlan(summary, groups.stream().map(group -> {
            RoadmapReplanAiResponse.CompressedGroup content = generated.get(group.groupKey());
            Milestone first = group.sources().getFirst().getMilestone();
            Milestone last = group.sources().getLast().getMilestone();
            return new RoadmapCompressionPlan.Group(
                    group.groupKey(), group.roadmapSkillId(), group.sources().stream()
                    .map(link -> link.getMilestone().getId()).toList(), group.assignedMinutes(),
                    group.learningOrder(), first.getStartLevel(), last.getTargetLevel(), content.title(),
                    content.description(), content.learningObjective(), content.completionCriteria(),
                    content.milestoneType(), content.difficulty(), content.compressionReason(),
                    content.learningResources().stream().map(resource ->
                            new RoadmapCompressionPlan.Resource(
                                    resource.resourceId(), resource.resourceType(), resource.title(),
                                    resource.description(), resource.provider(), resource.url(),
                                    resource.thumbnailUrl())).toList());
        }).toList(), snapshot(allLinks));
    }

    private List<RoadmapReplanPreviewResponse.CompressedSkill> previewSkills(RoadmapCompressionPlan plan) {
        return plan.groups().stream().collect(Collectors.groupingBy(
                        RoadmapCompressionPlan.Group::roadmapSkillId, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream().map(entry -> new RoadmapReplanPreviewResponse.CompressedSkill(
                        entry.getKey(), entry.getValue().stream().map(item ->
                        new RoadmapReplanPreviewResponse.CompressedMilestone(
                                item.sourceMilestoneIds(), item.title(), item.description(),
                                item.learningObjective(), item.completionCriteria(),
                                item.assignedEstimatedMinutes(), item.learningOrder(),
                                item.compressionReason(), item.learningResources().stream().map(resource ->
                                new RoadmapReplanPreviewResponse.LearningResource(
                                        resource.externalId(), resource.resourceType(), resource.title(),
                                        resource.description(), resource.provider(), resource.url(),
                                        resource.thumbnailUrl())).toList())).toList())).toList();
    }

    private List<RoadmapMilestone> candidates(List<RoadmapMilestone> links) {
        return links.stream()
                .filter(link -> link.getMilestone().getStatus() == MilestoneStatus.NOT_STARTED).toList();
    }

    private List<RoadmapMilestone> links(Long roadmapId) {
        List<Long> ids = skillRepository.findAllByRoadmapIdOrderByPriorityAscIdAsc(roadmapId)
                .stream().map(RoadmapSkill::getId).toList();
        return ids.isEmpty() ? List.of() : linkRepository.findAllByRoadmapSkillIds(ids);
    }

    private Roadmap activeRoadmap(Long roadmapId, Long userId) {
        if (roadmapId == null || roadmapId <= 0) throw invalid("Invalid roadmapId");
        Roadmap value = roadmapRepository.findByIdAndUserId(roadmapId, userId)
                .orElseThrow(() -> new RoadmapException(ErrorCode.ROADMAP_NOT_FOUND, "Roadmap not found"));
        if (value.getStatus() != RoadmapStatus.ACTIVE) throw invalid("Only active roadmaps can be replanned");
        return value;
    }

    private Roadmap activeRoadmapForUpdate(Long roadmapId, Long userId) {
        if (roadmapId == null || roadmapId <= 0) throw invalid("Invalid roadmapId");
        Roadmap value = roadmapRepository.findOwnedForUpdate(roadmapId, userId)
                .orElseThrow(() -> new RoadmapException(ErrorCode.ROADMAP_NOT_FOUND, "Roadmap not found"));
        if (value.getStatus() != RoadmapStatus.ACTIVE) throw invalid("Only active roadmaps can be replanned");
        return value;
    }

    private List<RoadmapCompressionPlan.SourceSnapshot> snapshot(List<RoadmapMilestone> links) {
        return links.stream().map(link -> new RoadmapCompressionPlan.SourceSnapshot(
                        link.getId(), link.getRoadmapSkill().getId(), link.getMilestone().getId(),
                        link.getLearningOrder(), link.isRequired(), link.getMilestone().getStatus()))
                .sorted(Comparator.comparing(RoadmapCompressionPlan.SourceSnapshot::linkId))
                .toList();
    }

    private RoadmapReplanApplyResponse response(Roadmap roadmap) {
        return new RoadmapReplanApplyResponse(
                roadmap.getId(), roadmap.getTotalEstimatedMinutes(), roadmap.getEstimatedEndDate());
    }

    private int remainingMinutes(Collection<RoadmapMilestone> values) {
        return values.stream()
                .filter(link -> link.getMilestone().getStatus() != MilestoneStatus.COMPLETED)
                .mapToInt(link -> link.getMilestone().getEstimatedMinutes()).sum();
    }

    private int totalMinutes(Collection<RoadmapMilestone> values) {
        return values.stream().mapToInt(link -> link.getMilestone().getEstimatedMinutes()).sum();
    }

    private RoadmapCompressionPlan read(JsonNode json) {
        try {
            return objectMapper.treeToValue(json, RoadmapCompressionPlan.class);
        } catch (JsonProcessingException exception) {
            throw invalid("Stored compression preview is invalid");
        }
    }

    private String instruction(RoadmapReplanPreviewRequest request) {
        return request == null || request.userInstruction() == null ? "" : request.userInstruction().trim();
    }

    private Long currentUserId() {
        Long value = currentUserIdProvider.getCurrentUserId();
        if (value == null || value <= 0) throw invalid("Invalid current user");
        return value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private RoadmapException invalid(String message) {
        return new RoadmapException(ErrorCode.ROADMAP_INVALID_REQUEST, message);
    }

    private static final class GroupDraft {
        private final String groupKey;
        private final Long roadmapSkillId;
        private final String skillName;
        private final RoadmapSkill skill;
        private final int learningOrder;
        private final List<RoadmapMilestone> sources;
        private final int originalMinutes;
        private int assignedMinutes;

        private GroupDraft(String groupKey, Long roadmapSkillId, String skillName, int learningOrder,
                           List<RoadmapMilestone> sources, int originalMinutes, int assignedMinutes) {
            this.groupKey = groupKey;
            this.roadmapSkillId = roadmapSkillId;
            this.skillName = skillName;
            this.skill = sources.getFirst().getRoadmapSkill();
            this.learningOrder = learningOrder;
            this.sources = List.copyOf(sources);
            this.originalMinutes = originalMinutes;
            this.assignedMinutes = assignedMinutes;
        }

        String groupKey() {
            return groupKey;
        }

        Long roadmapSkillId() {
            return roadmapSkillId;
        }

        String skillName() {
            return skillName;
        }

        RoadmapSkill skill() {
            return skill;
        }

        int learningOrder() {
            return learningOrder;
        }

        List<RoadmapMilestone> sources() {
            return sources;
        }

        int originalMinutes() {
            return originalMinutes;
        }

        int assignedMinutes() {
            return assignedMinutes;
        }

        void assignMinutes(int value) {
            assignedMinutes = value;
        }
    }
}
