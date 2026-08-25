package com.cenergy.passed_backend.domain.recommendation.application;

import com.cenergy.passed_backend.domain.recommendation.application.model.PostingSkillBundle;
import com.cenergy.passed_backend.domain.recommendation.application.model.VerifiedSkillMatch;
import com.cenergy.passed_backend.domain.recommendation.dto.UserSkillData;
import com.cenergy.passed_backend.domain.skill.entity.SkillCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationSkillVerificationServiceTest {

    @Test
    void addsOnlyAiVerifiedTargetSkillsWithoutOverwritingExactMatches() {
        RecommendationSkillVerificationClient client = mock(
                RecommendationSkillVerificationClient.class
        );
        RecommendationSkillVerificationService service =
                new RecommendationSkillVerificationService(client);
        List<UserSkillData> exactSkills = List.of(
                new UserSkillData(220L, (short) 1, true)
        );
        PostingSkillBundle bundle = new PostingSkillBundle(
                List.of(
                        postingSkill(220L, "개인정보 보호"),
                        postingSkill(339L, "보안"),
                        postingSkill(1146L, "장애 원인 분석")
                ),
                List.of(),
                List.of()
        );
        when(client.verify(216L, List.of(339L, 1146L))).thenReturn(List.of(
                verified(339L, 858L, (short) 2),
                verified(1146L, 1147L, (short) 2)
        ));

        List<UserSkillData> result = service.enrich(216L, List.of(bundle), exactSkills);

        assertEquals(List.of(
                new UserSkillData(220L, (short) 1, true),
                new UserSkillData(339L, (short) 2, false),
                new UserSkillData(1146L, (short) 2, false)
        ), result);
        verify(client).verify(216L, List.of(339L, 1146L));
    }

    @Test
    void fallsBackToExactIdSkillsWhenAiVerificationFails() {
        RecommendationSkillVerificationClient client = mock(
                RecommendationSkillVerificationClient.class
        );
        RecommendationSkillVerificationService service =
                new RecommendationSkillVerificationService(client);
        List<UserSkillData> exactSkills = List.of(
                new UserSkillData(220L, (short) 1, true)
        );
        PostingSkillBundle bundle = new PostingSkillBundle(
                List.of(postingSkill(339L, "보안")),
                List.of(),
                List.of()
        );
        when(client.verify(216L, List.of(339L)))
                .thenThrow(new IllegalStateException("AI unavailable"));

        List<UserSkillData> result = service.enrich(216L, List.of(bundle), exactSkills);

        assertEquals(exactSkills, result);
    }

    @Test
    void acceptsTargetLevelVerifiedDirectlyFromDocumentEvidence() {
        RecommendationSkillVerificationClient client = mock(
                RecommendationSkillVerificationClient.class
        );
        RecommendationSkillVerificationService service =
                new RecommendationSkillVerificationService(client);
        PostingSkillBundle bundle = new PostingSkillBundle(
                List.of(postingSkill(1339L, "정보보호 의식")),
                List.of(),
                List.of()
        );
        when(client.verify(216L, List.of(1339L))).thenReturn(List.of(
                new VerifiedSkillMatch(
                        1339L,
                        "정보보호 의식",
                        null,
                        null,
                        (short) 2,
                        "민감정보를 탐지하고 차단하는 필터를 적용했습니다.",
                        new BigDecimal("0.3595"),
                        "DIRECT_DOCUMENT_EVIDENCE"
                )
        ));

        List<UserSkillData> result = service.enrich(216L, List.of(bundle), List.of());

        assertEquals(List.of(new UserSkillData(1339L, (short) 2, false)), result);
    }

    private PostingSkillBundle.PostingSkill postingSkill(Long id, String name) {
        return new PostingSkillBundle.PostingSkill(
                id,
                name,
                SkillCategory.TECHNICAL_SKILL,
                (short) 2
        );
    }

    private VerifiedSkillMatch verified(Long targetId, Long sourceId, short level) {
        return new VerifiedSkillMatch(
                targetId,
                "target",
                sourceId,
                "source",
                level,
                "직접 수행한 근거",
                new BigDecimal("0.6500"),
                "TARGET_DIRECTLY_SUPPORTED"
        );
    }
}
