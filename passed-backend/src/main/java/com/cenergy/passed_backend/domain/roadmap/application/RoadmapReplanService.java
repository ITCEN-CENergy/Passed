package com.cenergy.passed_backend.domain.roadmap.application;

import com.cenergy.passed_backend.domain.roadmap.ai.client.RoadmapAiClient;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapReplanAiRequest;
import com.cenergy.passed_backend.domain.roadmap.ai.dto.RoadmapReplanAiResponse;
import com.cenergy.passed_backend.domain.roadmap.api.*;
import com.cenergy.passed_backend.domain.roadmap.entity.*;
import com.cenergy.passed_backend.domain.roadmap.repository.*;
import com.cenergy.passed_backend.global.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RoadmapReplanService {
    private final CurrentUserIdProvider currentUserIdProvider;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapSkillRepository skillRepository;
    private final RoadmapMilestoneRepository milestoneRepository;
    private final RoadmapReplanRepository replanRepository;
    private final RoadmapAiClient aiClient;
    private final RoadmapEtaCalculator etaCalculator;
    private final RoadmapProgressSynchronizer progressSynchronizer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RoadmapReplanService(CurrentUserIdProvider currentUserIdProvider,
                                RoadmapRepository roadmapRepository,
                                RoadmapSkillRepository skillRepository,
                                RoadmapMilestoneRepository milestoneRepository,
                                RoadmapReplanRepository replanRepository,
                                RoadmapAiClient aiClient,
                                RoadmapEtaCalculator etaCalculator,
                                RoadmapProgressSynchronizer progressSynchronizer) {
        this.currentUserIdProvider = currentUserIdProvider;
        this.roadmapRepository = roadmapRepository;
        this.skillRepository = skillRepository;
        this.milestoneRepository = milestoneRepository;
        this.replanRepository = replanRepository;
        this.aiClient = aiClient;
        this.etaCalculator = etaCalculator;
        this.progressSynchronizer = progressSynchronizer;
    }

    @Transactional
    public RoadmapReplanPreviewResponse preview(Long roadmapId, RoadmapReplanPreviewRequest request) {
        Long userId = currentUserId();
        Roadmap roadmap = activeRoadmap(roadmapId, userId);
        List<RoadmapMilestone> links = links(roadmapId);
        if (links.isEmpty()) throw invalid("Roadmap has no milestones");
        LocalDate currentEta = etaCalculator.calculate(links);
        RoadmapScheduleAssessment schedule = RoadmapScheduleAssessment.assess(
                roadmap.getBaselineEndDate(), currentEta);
        RoadmapReplanAiRequest aiRequest = new RoadmapReplanAiRequest(
                roadmapId, roadmap.getTitle(), schedule.delayDays(), instruction(request),
                links.stream().map(link -> new RoadmapReplanAiRequest.Milestone(
                        link.getMilestone().getId(), link.getRoadmapSkill().getId(),
                        link.getMilestone().getTitle(), link.getMilestone().getStatus(),
                        link.getMilestone().getEstimatedMinutes(), link.getLearningOrder(),
                        link.isRequired())).toList());
        RoadmapReplanAiResponse response = aiClient.replan(aiRequest);
        Map<Long, RoadmapReplanAiResponse.Decision> decisions = validate(aiRequest, response);
        JsonNode json = objectMapper.valueToTree(response);
        RoadmapReplan replan = replanRepository.save(
                RoadmapReplan.ready(roadmapId, userId, response.summary(), json));

        List<RoadmapMilestone> kept = links.stream()
                .filter(link -> decisions.get(link.getMilestone().getId()).action()
                        == RoadmapReplanAiResponse.Action.KEEP).toList();
        int previousMinutes = remainingMinutes(links);
        int replannedMinutes = remainingMinutes(kept);
        List<RoadmapReplanPreviewResponse.Change> changes = links.stream().map(link -> {
            RoadmapReplanAiResponse.Decision decision = decisions.get(link.getMilestone().getId());
            return new RoadmapReplanPreviewResponse.Change(
                    link.getMilestone().getId(), link.getMilestone().getTitle(), decision.action(),
                    link.getLearningOrder(), decision.learningOrder(),
                    link.getMilestone().getEstimatedMinutes(), decision.reason());
        }).toList();
        return new RoadmapReplanPreviewResponse(roadmapId, replan.getToken(), response.summary(),
                previousMinutes, replannedMinutes, currentEta, etaCalculator.calculate(kept), changes);
    }

    @Transactional
    public RoadmapReplanApplyResponse apply(Long roadmapId, RoadmapReplanApplyRequest request) {
        Long userId = currentUserId();
        activeRoadmap(roadmapId, userId);
        RoadmapReplan replan = replanRepository.findByTokenAndRoadmapIdAndUserIdAndStatus(
                        request.replanToken(), roadmapId, userId, RoadmapReplanStatus.READY)
                .orElseThrow(() -> invalid("Replan token is invalid or already applied"));
        List<RoadmapMilestone> links = links(roadmapId);
        RoadmapReplanAiResponse response = read(replan.getDecisionsJson());
        RoadmapReplanAiRequest current = new RoadmapReplanAiRequest(
                roadmapId, "current", 0, "",
                links.stream().map(link -> new RoadmapReplanAiRequest.Milestone(
                        link.getMilestone().getId(), link.getRoadmapSkill().getId(),
                        link.getMilestone().getTitle(), link.getMilestone().getStatus(),
                        link.getMilestone().getEstimatedMinutes(), link.getLearningOrder(),
                        link.isRequired())).toList());
        Map<Long, RoadmapReplanAiResponse.Decision> decisions = validate(current, response);
        List<RoadmapMilestone> removed = new ArrayList<>();
        List<RoadmapMilestone> kept = new ArrayList<>();
        for (RoadmapMilestone link : links) {
            RoadmapReplanAiResponse.Decision decision = decisions.get(link.getMilestone().getId());
            if (decision.action() == RoadmapReplanAiResponse.Action.REMOVE) {
                removed.add(link);
            } else {
                kept.add(link);
            }
        }
        milestoneRepository.deleteAll(removed);
        milestoneRepository.flush();

        // Moving order 4 to 3 would collide with the row currently at 3 before
        // Hibernate deletes/updates every row. Move kept links to a collision-free
        // temporary range, flush, and only then assign the approved final orders.
        for (int index = 0; index < kept.size(); index++) {
            kept.get(index).reorder(1_000_000 + index);
        }
        milestoneRepository.flush();
        for (RoadmapMilestone link : kept) {
            link.reorder(decisions.get(link.getMilestone().getId()).learningOrder());
        }
        milestoneRepository.flush();

        replan.markApplied(OffsetDateTime.now());
        progressSynchronizer.synchronizeRoadmap(roadmapId);
        Roadmap updated = roadmapRepository.findById(roadmapId).orElseThrow();
        return new RoadmapReplanApplyResponse(
                roadmapId, updated.getTotalEstimatedMinutes(), updated.getEstimatedEndDate());
    }

    private Map<Long, RoadmapReplanAiResponse.Decision> validate(
            RoadmapReplanAiRequest request, RoadmapReplanAiResponse response) {
        if (response == null || response.summary() == null || response.summary().isBlank()
                || response.decisions() == null) throw invalid("Invalid replan AI response");
        Map<Long, RoadmapReplanAiRequest.Milestone> requested = request.milestones().stream()
                .collect(Collectors.toMap(RoadmapReplanAiRequest.Milestone::milestoneId, Function.identity()));
        Map<Long, RoadmapReplanAiResponse.Decision> decisions = new LinkedHashMap<>();
        for (RoadmapReplanAiResponse.Decision decision : response.decisions()) {
            if (decision == null || decision.milestoneId() == null || decision.action() == null
                    || decision.reason() == null || decision.reason().isBlank()
                    || decisions.putIfAbsent(decision.milestoneId(), decision) != null) {
                throw invalid("Invalid or duplicate replan decision");
            }
            RoadmapReplanAiRequest.Milestone milestone = requested.get(decision.milestoneId());
            if (milestone == null) throw invalid("Unexpected milestone in replan decision");
            if ((milestone.status() != MilestoneStatus.NOT_STARTED || !milestone.required())
                    && decision.action() != RoadmapReplanAiResponse.Action.KEEP) {
                throw invalid("Protected milestone cannot be removed");
            }
            if (decision.action() == RoadmapReplanAiResponse.Action.KEEP
                    && (decision.learningOrder() == null || decision.learningOrder() <= 0)) {
                throw invalid("Kept milestone requires a positive learning order");
            }
            if (decision.action() == RoadmapReplanAiResponse.Action.REMOVE
                    && decision.learningOrder() != null) {
                throw invalid("Removed milestone must not have a learning order");
            }
        }
        if (!decisions.keySet().equals(requested.keySet())) throw invalid("Replan decisions are incomplete");
        Map<Long, List<RoadmapReplanAiRequest.Milestone>> bySkill = request.milestones().stream()
                .collect(Collectors.groupingBy(RoadmapReplanAiRequest.Milestone::roadmapSkillId));
        for (List<RoadmapReplanAiRequest.Milestone> skillMilestones : bySkill.values()) {
            List<RoadmapReplanAiResponse.Decision> kept = skillMilestones.stream()
                    .map(item -> decisions.get(item.milestoneId()))
                    .filter(item -> item.action() == RoadmapReplanAiResponse.Action.KEEP).toList();
            if (kept.isEmpty()) throw invalid("Every skill must keep at least one milestone");
            long uniqueOrders = kept.stream().map(RoadmapReplanAiResponse.Decision::learningOrder).distinct().count();
            if (uniqueOrders != kept.size()) throw invalid("Learning orders must be unique within a skill");
        }
        return decisions;
    }

    private List<RoadmapMilestone> links(Long roadmapId) {
        List<Long> skillIds = skillRepository.findAllByRoadmapIdOrderByPriorityAscIdAsc(roadmapId)
                .stream().map(RoadmapSkill::getId).toList();
        return skillIds.isEmpty() ? List.of() : milestoneRepository.findAllByRoadmapSkillIds(skillIds);
    }

    private Roadmap activeRoadmap(Long roadmapId, Long userId) {
        if (roadmapId == null || roadmapId <= 0) throw invalid("Invalid roadmapId");
        Roadmap roadmap = roadmapRepository.findByIdAndUserId(roadmapId, userId)
                .orElseThrow(() -> new RoadmapException(ErrorCode.ROADMAP_NOT_FOUND, "Roadmap not found"));
        if (roadmap.getStatus() != RoadmapStatus.ACTIVE) throw invalid("Only active roadmaps can be replanned");
        return roadmap;
    }

    private int remainingMinutes(Collection<RoadmapMilestone> links) {
        return links.stream().filter(RoadmapMilestone::isRequired)
                .filter(link -> link.getMilestone().getStatus() != MilestoneStatus.COMPLETED)
                .mapToInt(link -> link.getMilestone().getEstimatedMinutes()).sum();
    }

    private String instruction(RoadmapReplanPreviewRequest request) {
        return request == null || request.userInstruction() == null ? "" : request.userInstruction().trim();
    }

    private Long currentUserId() {
        Long userId = currentUserIdProvider.getCurrentUserId();
        if (userId == null || userId <= 0) throw invalid("Invalid current user");
        return userId;
    }

    private RoadmapReplanAiResponse read(JsonNode json) {
        try { return objectMapper.treeToValue(json, RoadmapReplanAiResponse.class); }
        catch (JsonProcessingException exception) { throw invalid("Stored replan preview is invalid"); }
    }

    private RoadmapException invalid(String message) {
        return new RoadmapException(ErrorCode.ROADMAP_INVALID_REQUEST, message);
    }
}
