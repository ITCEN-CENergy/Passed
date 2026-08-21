package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.PostingSkillBundle;
import com.cenergy.passed_backend.domain.recommendation.application.model.VerifiedSkillMatch;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class RecommendationSkillVerificationService {
    private static final Logger log = LoggerFactory.getLogger(
            RecommendationSkillVerificationService.class
    );
    private static final int REQUEST_BATCH_SIZE = 20;

    private final RecommendationSkillVerificationClient verificationClient;

    public RecommendationSkillVerificationService(
            RecommendationSkillVerificationClient verificationClient
    ) {
        this.verificationClient = verificationClient;
    }

    public List<UserSkillData> enrich(
            Long userId,
            Collection<PostingSkillBundle> postingBundles,
            Collection<UserSkillData> userSkills
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(postingBundles, "postingBundles must not be null");
        Objects.requireNonNull(userSkills, "userSkills must not be null");

        Map<Long, UserSkillData> effective = new LinkedHashMap<>();
        for (UserSkillData skill : userSkills) {
            effective.put(skill.skillId(), skill);
        }
        List<Long> targets = targetSkillIds(postingBundles, effective.keySet());
        if (targets.isEmpty()) {
            return sorted(effective.values());
        }

        Set<Long> allowedTargets = Set.copyOf(targets);
        try {
            for (int start = 0; start < targets.size(); start += REQUEST_BATCH_SIZE) {
                int end = Math.min(start + REQUEST_BATCH_SIZE, targets.size());
                for (VerifiedSkillMatch verified : verificationClient.verify(
                        userId,
                        targets.subList(start, end)
                )) {
                    validate(verified, allowedTargets);
                    effective.putIfAbsent(
                            verified.targetSkillId(),
                            new UserSkillData(
                                    verified.targetSkillId(),
                                    verified.inferredLevel(),
                                    false
                            )
                    );
                    log.info(
                            "AI verified recommendation skill userId={} targetSkillId={} "
                                    + "sourceSkillId={} similarity={} relationship={}",
                            userId,
                            verified.targetSkillId(),
                            verified.sourceSkillId(),
                            verified.similarity(),
                            verified.relationship()
                    );
                }
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "Recommendation skill verification failed; exact-ID matching will be used: {}",
                    exception.getMessage()
            );
            log.debug("Recommendation skill verification failure detail", exception);
            return sorted(userSkills);
        }
        return sorted(effective.values());
    }

    private List<Long> targetSkillIds(
            Collection<PostingSkillBundle> bundles,
            Set<Long> ownedSkillIds
    ) {
        Set<Long> targets = new LinkedHashSet<>();
        for (PostingSkillBundle bundle : bundles) {
            addTargets(targets, bundle.requiredSkills(), ownedSkillIds);
            addTargets(targets, bundle.preferredSkills(), ownedSkillIds);
            addTargets(targets, bundle.relatedSkills(), ownedSkillIds);
        }
        return targets.stream().sorted().toList();
    }

    private void addTargets(
            Set<Long> targets,
            List<PostingSkillBundle.PostingSkill> skills,
            Set<Long> ownedSkillIds
    ) {
        skills.stream()
                .map(PostingSkillBundle.PostingSkill::skillId)
                .filter(skillId -> !ownedSkillIds.contains(skillId))
                .forEach(targets::add);
    }

    private void validate(VerifiedSkillMatch value, Set<Long> allowedTargets) {
        if (value == null
                || value.targetSkillId() == null
                || !allowedTargets.contains(value.targetSkillId())
                || value.inferredLevel() < 1
                || value.inferredLevel() > 3
                || value.evidence() == null
                || value.evidence().isBlank()
                || value.similarity() == null
                || value.similarity().signum() < 0
                || value.similarity().compareTo(java.math.BigDecimal.ONE) > 0
                || value.relationship() == null
                || !(value.relationship().equals("SAME_SKILL")
                || value.relationship().equals("TARGET_DIRECTLY_SUPPORTED")
                || value.relationship().equals("DIRECT_DOCUMENT_EVIDENCE"))
                || !hasValidSource(value)) {
            throw new IllegalStateException("Recommendation AI returned an invalid skill match");
        }
    }

    private boolean hasValidSource(VerifiedSkillMatch value) {
        if (value.relationship().equals("DIRECT_DOCUMENT_EVIDENCE")) {
            return value.sourceSkillId() == null && value.sourceSkillName() == null;
        }
        return value.sourceSkillId() != null
                && value.sourceSkillId() > 0
                && value.sourceSkillName() != null
                && !value.sourceSkillName().isBlank();
    }

    private List<UserSkillData> sorted(Collection<UserSkillData> values) {
        List<UserSkillData> result = new ArrayList<>(values);
        result.sort(Comparator.comparing(UserSkillData::skillId));
        return List.copyOf(result);
    }
}
