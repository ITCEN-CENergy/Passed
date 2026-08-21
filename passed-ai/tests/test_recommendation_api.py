from fastapi.testclient import TestClient
import pytest
from pydantic import ValidationError

from api.features.recommendation import router as recommendation_router
from api.features.recommendation.config import RecommendationSettings
from api.features.recommendation.exceptions import (
    RecommendationExplanationGenerationError,
)
from api.features.recommendation.schema import (
    DirectSkillEvidence,
    DirectSkillVerificationCandidate,
    DirectSkillVerificationModelResponse,
    DirectSkillVerificationSelection,
    RecommendationExplanationItem,
    RecommendationExplanationRequest,
    RecommendationExplanationResponse,
    SkillEvidence,
    SkillVerificationCandidate,
    SkillVerificationModelResponse,
    SkillVerificationRequest,
    SkillVerificationSelection,
)
from api.features.recommendation.service import generate_recommendation_explanations
from api.features.recommendation.skill_verification import (
    verify_recommendation_skills,
)
from app.main import app


client = TestClient(app)


def test_recommendation_model_is_limited_to_gpt_4_family() -> None:
    settings = RecommendationSettings(
        _env_file=None,
        OPENAI_API_KEY="test",
        RECOMMENDATION_LLM_MODEL="gpt-4o-mini",
    )
    assert settings.model == "gpt-4o-mini"

    with pytest.raises(ValidationError):
        RecommendationSettings(
            _env_file=None,
            OPENAI_API_KEY="test",
            RECOMMENDATION_LLM_MODEL="gpt-5",
        )


def explanation_input(job_posting_id: int = 101) -> dict:
    return {
        "jobPostingId": job_posting_id,
        "jobPostingTitle": "백엔드 개발자",
        "companyName": "Passed",
        "posting": {
            "positionDetail": "AI 기반 서비스를 개발합니다.",
            "mainDuty": "백엔드 API 개발과 운영",
            "qualification": "Java 서비스 개발 역량",
            "preference": "Docker 활용 역량",
            "companyTalentProfile": "주도적으로 문제를 해결하는 인재",
        },
        "matchedSkills": [
            {
                "skillName": "Java",
                "skillType": "REQUIRED",
                "userLevel": 2,
                "requiredLevel": 2,
                "matchRate": "1.0000",
                "requirementSatisfied": True,
            }
        ],
        "gapSkills": [],
    }


def request_payload(*job_posting_ids: int) -> dict:
    return {
        "recommendations": [
            explanation_input(job_posting_id) for job_posting_id in job_posting_ids
        ]
    }


def response_for(job_posting_id: int) -> RecommendationExplanationResponse:
    return RecommendationExplanationResponse(
        recommendations=[
            RecommendationExplanationItem(
                jobPostingId=job_posting_id,
                reason=(
                    "Java 역량이 백엔드 API 개발 업무와 연결됩니다. "
                    "이 역량을 기반으로 서비스 운영 범위까지 성장할 수 있습니다."
                ),
            )
        ]
    )


def test_endpoint_returns_camel_case_structured_response(monkeypatch) -> None:
    async def generate(request: RecommendationExplanationRequest):
        return response_for(request.recommendations[0].jobPostingId)

    monkeypatch.setattr(
        recommendation_router,
        "generate_recommendation_explanations",
        generate,
    )

    response = client.post(
        "/api/v1/recommendations/explanations",
        json=request_payload(101),
    )

    assert response.status_code == 200
    assert response.json() == {
        "recommendations": [
            {
                "jobPostingId": 101,
                "reason": (
                    "Java 역량이 백엔드 API 개발 업무와 연결됩니다. "
                    "이 역량을 기반으로 서비스 운영 범위까지 성장할 수 있습니다."
                ),
            }
        ]
    }


def test_endpoint_rejects_duplicated_job_posting_ids() -> None:
    response = client.post(
        "/api/v1/recommendations/explanations",
        json=request_payload(101, 101),
    )

    assert response.status_code == 422


def test_router_is_registered() -> None:
    assert "/api/v1/recommendations/explanations" in app.openapi()["paths"]
    assert "/api/v1/recommendations/skill-verifications" in app.openapi()["paths"]


@pytest.mark.asyncio
async def test_service_returns_generator_output() -> None:
    request = RecommendationExplanationRequest.model_validate(request_payload(101))

    class Generator:
        async def generate(
            self,
            value: RecommendationExplanationRequest,
        ) -> RecommendationExplanationResponse:
            assert value is request
            return response_for(101)

    response = await generate_recommendation_explanations(request, Generator())

    assert response == response_for(101)


@pytest.mark.asyncio
async def test_service_rejects_response_with_unrequested_posting_id() -> None:
    request = RecommendationExplanationRequest.model_validate(request_payload(101))

    class Generator:
        async def generate(
            self,
            value: RecommendationExplanationRequest,
        ) -> RecommendationExplanationResponse:
            return response_for(999)

    with pytest.raises(RecommendationExplanationGenerationError):
        await generate_recommendation_explanations(request, Generator())


def verification_candidate() -> SkillVerificationCandidate:
    return SkillVerificationCandidate(
        targetSkillId=339,
        targetSkillName="보안",
        targetSkillCategory="TECHNICAL_SKILL",
        targetSkillDescription="정보의 기밀성·무결성·가용성을 지키는 역량",
        sourceSkillId=858,
        sourceSkillName="보안 필터 적용",
        sourceSkillCategory="EXPERIENCE",
        sourceSkillDescription="민감정보를 탐지하고 차단하는 필터 적용 경험",
        similarity=0.6576,
        evidences=[
            SkillEvidence(
                evidenceId=77,
                text="개인정보가 전달되지 않도록 개인정보 탐지 및 보안 필터를 적용했습니다.",
                extractedLevel=2,
            )
        ],
    )


@pytest.mark.asyncio
async def test_skill_verification_materializes_only_allowed_evidence() -> None:
    request = SkillVerificationRequest(userId=216, targetSkillIds=[339])

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            assert candidates == [verification_candidate()]
            return SkillVerificationModelResponse(
                verified=[
                    SkillVerificationSelection(
                        targetSkillId=339,
                        sourceSkillId=858,
                        evidenceId=77,
                        evidenceQuote="개인정보 탐지 및 보안 필터를 적용했습니다.",
                        relationship="TARGET_DIRECTLY_SUPPORTED",
                    )
                ]
            )

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [verification_candidate()],
    )

    assert response.model_dump() == {
        "verifiedSkills": [
            {
                "targetSkillId": 339,
                "targetSkillName": "보안",
                "sourceSkillId": 858,
                "sourceSkillName": "보안 필터 적용",
                "inferredLevel": 2,
                "evidence": "개인정보 탐지 및 보안 필터를 적용했습니다.",
                "similarity": 0.6576,
                "relationship": "TARGET_DIRECTLY_SUPPORTED",
            }
        ]
    }


@pytest.mark.asyncio
async def test_skill_verification_recovers_direct_evidence_after_unoffered_evidence_id() -> None:
    request = SkillVerificationRequest(userId=216, targetSkillIds=[339])

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            return SkillVerificationModelResponse(
                verified=[
                    SkillVerificationSelection(
                        targetSkillId=339,
                        sourceSkillId=858,
                        evidenceId=999,
                        evidenceQuote="개인정보 탐지 및 보안 필터를 적용했습니다.",
                        relationship="TARGET_DIRECTLY_SUPPORTED",
                    )
                ]
            )

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [verification_candidate()],
    )

    assert [skill.targetSkillId for skill in response.verifiedSkills] == [339]


@pytest.mark.asyncio
async def test_skill_verification_accepts_valid_selection_after_invalid_duplicate() -> None:
    request = SkillVerificationRequest(userId=216, targetSkillIds=[339])

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            return SkillVerificationModelResponse(
                verified=[
                    SkillVerificationSelection(
                        targetSkillId=339,
                        sourceSkillId=858,
                        evidenceId=999,
                        evidenceQuote="보안 필터를 적용했습니다.",
                        relationship="TARGET_DIRECTLY_SUPPORTED",
                    ),
                    SkillVerificationSelection(
                        targetSkillId=339,
                        sourceSkillId=858,
                        evidenceId=77,
                        evidenceQuote="보안 필터를 적용했습니다.",
                        relationship="TARGET_DIRECTLY_SUPPORTED",
                    ),
                ]
            )

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [verification_candidate()],
    )

    assert [skill.targetSkillId for skill in response.verifiedSkills] == [339]


@pytest.mark.asyncio
async def test_skill_verification_skips_llm_when_retrieval_has_no_candidates() -> None:
    request = SkillVerificationRequest(userId=216, targetSkillIds=[339])

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            raise AssertionError("generator must not be called")

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [],
    )

    assert response.verifiedSkills == []


@pytest.mark.asyncio
async def test_skill_verification_recovers_explicit_root_cause_evidence() -> None:
    request = SkillVerificationRequest(userId=216, targetSkillIds=[1146])
    candidate = SkillVerificationCandidate(
        targetSkillId=1146,
        targetSkillName="장애 원인 분석",
        targetSkillCategory="EXPERIENCE",
        targetSkillDescription="자료를 분석하여 장애 원인을 특정하는 역량",
        sourceSkillId=1147,
        sourceSkillName="장애 재발 방지",
        sourceSkillCategory="EXPERIENCE",
        sourceSkillDescription="장애 재발 방지 대책을 적용하는 역량",
        similarity=0.5811,
        evidences=[
            SkillEvidence(
                evidenceId=103,
                text=(
                    "반복 장애가 발생했을 때 오류 로그와 재현 테스트를 분석해 "
                    "원인을 특정하고 재시도 로직을 수정했습니다."
                ),
                extractedLevel=2,
            )
        ],
    )

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            return SkillVerificationModelResponse(verified=[])

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [candidate],
    )

    assert len(response.verifiedSkills) == 1
    assert response.verifiedSkills[0].targetSkillId == 1146
    assert response.verifiedSkills[0].sourceSkillId == 1147
    assert "오류 로그와 재현 테스트를 분석해 원인을 특정" in (
        response.verifiedSkills[0].evidence
    )


@pytest.mark.asyncio
async def test_skill_verification_recovers_root_cause_after_ungrounded_llm_quote() -> None:
    request = SkillVerificationRequest(userId=216, targetSkillIds=[1146])
    candidate = SkillVerificationCandidate(
        targetSkillId=1146,
        targetSkillName="장애 원인 분석",
        targetSkillCategory="EXPERIENCE",
        targetSkillDescription="자료를 분석하여 장애 원인을 특정하는 역량",
        sourceSkillId=1147,
        sourceSkillName="장애 재발 방지",
        sourceSkillCategory="EXPERIENCE",
        sourceSkillDescription="장애 재발 방지 대책을 적용하는 역량",
        similarity=0.5811,
        evidences=[
            SkillEvidence(
                evidenceId=103,
                text=(
                    "반복 장애가 발생했을 때 오류 로그와 재현 테스트를 분석해 "
                    "원인을 특정하고 재시도 로직을 수정했습니다."
                ),
                extractedLevel=2,
            )
        ],
    )

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            return SkillVerificationModelResponse(
                verified=[
                    SkillVerificationSelection(
                        targetSkillId=1146,
                        sourceSkillId=1147,
                        evidenceId=103,
                        evidenceQuote="장애 로그를 분석하여 근본 원인을 찾아냈습니다.",
                        relationship="TARGET_DIRECTLY_SUPPORTED",
                    )
                ]
            )

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [candidate],
    )

    assert [skill.targetSkillId for skill in response.verifiedSkills] == [1146]
    assert "오류 로그와 재현 테스트를 분석해 원인을 특정" in (
        response.verifiedSkills[0].evidence
    )


@pytest.mark.asyncio
async def test_skill_verification_rejects_chatbot_as_voice_ai() -> None:
    request = SkillVerificationRequest(userId=216, targetSkillIds=[1083])
    candidate = SkillVerificationCandidate(
        targetSkillId=1083,
        targetSkillName="음성 AI 프로젝트",
        targetSkillCategory="EXPERIENCE",
        targetSkillDescription="음성을 인식하거나 합성하는 AI 프로젝트 경험",
        sourceSkillId=12,
        sourceSkillName="AI 챗봇",
        sourceSkillCategory="TECHNICAL_SKILL",
        sourceSkillDescription="대화형 답변을 제공하는 AI 서비스 구현 역량",
        similarity=0.5602,
        evidences=[
            SkillEvidence(
                evidenceId=109,
                text="검색된 문서를 LLM에 전달하는 AI 챗봇을 개발했습니다.",
                extractedLevel=2,
            )
        ],
    )

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            return SkillVerificationModelResponse(
                verified=[
                    SkillVerificationSelection(
                        targetSkillId=1083,
                        sourceSkillId=12,
                        evidenceId=109,
                        evidenceQuote="AI 챗봇을 개발했습니다.",
                        relationship="TARGET_DIRECTLY_SUPPORTED",
                    )
                ]
            )

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [candidate],
    )

    assert response.verifiedSkills == []


@pytest.mark.asyncio
async def test_skill_verification_rejects_filter_application_as_guardrail_design() -> None:
    request = SkillVerificationRequest(userId=216, targetSkillIds=[850])
    candidate = SkillVerificationCandidate(
        targetSkillId=850,
        targetSkillName="보안 가드레일 설계",
        targetSkillCategory="EXPERIENCE",
        targetSkillDescription="보안 통제의 구조와 규칙을 설계하는 역량",
        sourceSkillId=858,
        sourceSkillName="보안 필터 적용",
        sourceSkillCategory="EXPERIENCE",
        sourceSkillDescription="보안 필터를 실제 업무에 적용하는 역량",
        similarity=0.5931,
        evidences=[
            SkillEvidence(
                evidenceId=96,
                text="개인정보를 탐지하고 차단하는 보안 필터를 적용했습니다.",
                extractedLevel=2,
            )
        ],
    )

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            return SkillVerificationModelResponse(
                verified=[
                    SkillVerificationSelection(
                        targetSkillId=850,
                        sourceSkillId=858,
                        evidenceId=96,
                        evidenceQuote="보안 필터를 적용했습니다.",
                        relationship="TARGET_DIRECTLY_SUPPORTED",
                    )
                ]
            )

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [candidate],
    )

    assert response.verifiedSkills == []


@pytest.mark.asyncio
async def test_skill_verification_does_not_recover_skill_from_coursework_list() -> None:
    request = SkillVerificationRequest(userId=216, targetSkillIds=[1339])
    candidate = SkillVerificationCandidate(
        targetSkillId=1339,
        targetSkillName="정보보호 의식",
        targetSkillCategory="BEHAVIORAL_TRAIT",
        targetSkillDescription="기밀정보를 안전하게 다루는 태도",
        sourceSkillId=220,
        sourceSkillName="개인정보 보호",
        sourceSkillCategory="TECHNICAL_SKILL",
        sourceSkillDescription="개인정보를 안전하게 다루는 역량",
        similarity=0.6,
        evidences=[
            SkillEvidence(
                evidenceId=145,
                text="교육 내용: LLM API, RAG, 개인정보 보호와 비용 최적화",
                extractedLevel=1,
            )
        ],
    )

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            return SkillVerificationModelResponse(verified=[])

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [candidate],
    )

    assert response.verifiedSkills == []


@pytest.mark.asyncio
async def test_skill_verification_uses_direct_action_evidence_for_missing_target() -> None:
    request = SkillVerificationRequest(userId=216, targetSkillIds=[1339])
    direct_candidate = DirectSkillVerificationCandidate(
        targetSkillId=1339,
        targetSkillName="정보보호 의식",
        targetSkillCategory="BEHAVIORAL_TRAIT",
        targetSkillDescription="기밀정보를 안전하게 다루는 태도",
        evidences=[
            DirectSkillEvidence(
                sourceKind="COVER_LETTER",
                chunkId=13,
                contextType="EXPERIENCE",
                text=(
                    "민감정보가 외부 API로 전달되지 않도록 탐지하고 "
                    "차단하는 보안 필터를 적용했습니다."
                ),
                similarity=0.3595,
            )
        ],
    )

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            return SkillVerificationModelResponse(verified=[])

        async def verify_direct(
            self,
            candidates: list[DirectSkillVerificationCandidate],
        ):
            assert candidates == [direct_candidate]
            return DirectSkillVerificationModelResponse(
                verified=[
                    DirectSkillVerificationSelection(
                        targetSkillId=1339,
                        sourceKind="COVER_LETTER",
                        chunkId=13,
                        evidenceQuote=(
                            "민감정보가 외부 API로 전달되지 않도록 탐지하고 "
                            "차단하는 보안 필터를 적용했습니다."
                        ),
                        inferredLevel=2,
                    )
                ]
            )

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [],
        direct_loader=lambda user_id, target_ids: [direct_candidate],
    )

    assert response.model_dump() == {
        "verifiedSkills": [
            {
                "targetSkillId": 1339,
                "targetSkillName": "정보보호 의식",
                "sourceSkillId": None,
                "sourceSkillName": None,
                "inferredLevel": 2,
                "evidence": (
                    "민감정보가 외부 API로 전달되지 않도록 탐지하고 "
                    "차단하는 보안 필터를 적용했습니다."
                ),
                "similarity": 0.3595,
                "relationship": "DIRECT_DOCUMENT_EVIDENCE",
            }
        ]
    }


@pytest.mark.asyncio
async def test_inferred_verification_does_not_copy_source_skill_level() -> None:
    request = SkillVerificationRequest(userId=216, targetSkillIds=[339])
    candidate = verification_candidate().model_copy(
        update={
            "evidences": [
                SkillEvidence(
                    evidenceId=77,
                    text="개인정보 탐지 및 보안 필터를 적용했습니다.",
                    extractedLevel=3,
                )
            ]
        }
    )

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            return SkillVerificationModelResponse(
                verified=[
                    SkillVerificationSelection(
                        targetSkillId=339,
                        sourceSkillId=858,
                        evidenceId=77,
                        evidenceQuote="개인정보 탐지 및 보안 필터를 적용했습니다.",
                        relationship="TARGET_DIRECTLY_SUPPORTED",
                    )
                ]
            )

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [candidate],
    )

    assert response.verifiedSkills[0].inferredLevel == 2


@pytest.mark.asyncio
async def test_direct_verification_rejects_chatbot_evidence_for_voice_ai() -> None:
    request = SkillVerificationRequest(userId=216, targetSkillIds=[1083])
    direct_candidate = DirectSkillVerificationCandidate(
        targetSkillId=1083,
        targetSkillName="음성 AI 프로젝트",
        targetSkillCategory="EXPERIENCE",
        targetSkillDescription="음성을 인식하거나 합성하는 AI 프로젝트 경험",
        evidences=[
            DirectSkillEvidence(
                sourceKind="RESUME",
                chunkId=13,
                contextType="AWARD",
                text="LLM API와 RAG를 적용한 콘텐츠 생성 서비스를 구현했습니다.",
                similarity=0.5081,
            )
        ],
    )

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            return SkillVerificationModelResponse(verified=[])

        async def verify_direct(
            self,
            candidates: list[DirectSkillVerificationCandidate],
        ):
            return DirectSkillVerificationModelResponse(
                verified=[
                    DirectSkillVerificationSelection(
                        targetSkillId=1083,
                        sourceKind="RESUME",
                        chunkId=13,
                        evidenceQuote=(
                            "LLM API와 RAG를 적용한 콘텐츠 생성 서비스를 구현했습니다."
                        ),
                        inferredLevel=2,
                    )
                ]
            )

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [],
        direct_loader=lambda user_id, target_ids: [direct_candidate],
    )

    assert response.verifiedSkills == []


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("target_skill_id", "target_name", "target_category"),
    [
        (282, "데이터 분석", "TECHNICAL_SKILL"),
        (740, "데이터 세트 구축", "EXPERIENCE"),
    ],
)
async def test_skill_verification_rejects_certification_as_non_certification_skill(
    target_skill_id: int,
    target_name: str,
    target_category: str,
) -> None:
    request = SkillVerificationRequest(
        userId=216,
        targetSkillIds=[target_skill_id],
    )
    candidate = SkillVerificationCandidate(
        targetSkillId=target_skill_id,
        targetSkillName=target_name,
        targetSkillCategory=target_category,
        targetSkillDescription="데이터 관련 역량",
        sourceSkillId=1498,
        sourceSkillName="빅데이터분석기사",
        sourceSkillCategory="CERTIFICATION",
        sourceSkillDescription="빅데이터 분석 능력을 인증하는 국가기술자격",
        similarity=0.67,
        evidences=[
            SkillEvidence(
                evidenceId=94,
                text="자격증: 빅데이터분석기사",
                extractedLevel=2,
            )
        ],
    )

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            return SkillVerificationModelResponse(
                verified=[
                    SkillVerificationSelection(
                        targetSkillId=target_skill_id,
                        sourceSkillId=1498,
                        evidenceId=94,
                        evidenceQuote="자격증: 빅데이터분석기사",
                        relationship="TARGET_DIRECTLY_SUPPORTED",
                    )
                ]
            )

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [candidate],
    )

    assert response.verifiedSkills == []


@pytest.mark.asyncio
async def test_skill_verification_rejects_non_certification_as_certification() -> None:
    request = SkillVerificationRequest(userId=216, targetSkillIds=[1498])
    candidate = SkillVerificationCandidate(
        targetSkillId=1498,
        targetSkillName="빅데이터분석기사",
        targetSkillCategory="CERTIFICATION",
        targetSkillDescription="빅데이터 분석 국가기술자격",
        sourceSkillId=282,
        sourceSkillName="데이터 분석",
        sourceSkillCategory="TECHNICAL_SKILL",
        sourceSkillDescription="데이터를 분석하여 의사결정 근거를 도출하는 역량",
        similarity=0.67,
        evidences=[
            SkillEvidence(
                evidenceId=200,
                text="사용자 데이터를 분석해 패턴을 도출했습니다.",
                extractedLevel=2,
            )
        ],
    )

    class Generator:
        async def verify(self, candidates: list[SkillVerificationCandidate]):
            return SkillVerificationModelResponse(
                verified=[
                    SkillVerificationSelection(
                        targetSkillId=1498,
                        sourceSkillId=282,
                        evidenceId=200,
                        evidenceQuote="사용자 데이터를 분석해 패턴을 도출했습니다.",
                        relationship="TARGET_DIRECTLY_SUPPORTED",
                    )
                ]
            )

    response = await verify_recommendation_skills(
        request,
        Generator(),
        loader=lambda user_id, target_ids: [candidate],
    )

    assert response.verifiedSkills == []
