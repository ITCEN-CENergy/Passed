from fastapi.testclient import TestClient
import pytest

from api.features.recommendation import router as recommendation_router
from api.features.recommendation.exceptions import (
    RecommendationExplanationGenerationError,
)
from api.features.recommendation.schema import (
    RecommendationExplanationItem,
    RecommendationExplanationRequest,
    RecommendationExplanationResponse,
)
from api.features.recommendation.service import generate_recommendation_explanations
from app.main import app


client = TestClient(app)


def explanation_input(job_posting_id: int = 101) -> dict:
    return {
        "jobPostingId": job_posting_id,
        "jobPostingTitle": "백엔드 개발자",
        "companyName": "Passed",
        "rankOrder": 1,
        "recommendationGrade": "RECOMMENDED",
        "candidateTier": "PRIMARY",
        "totalScore": "75.0000",
        "requiredScore": "70.0000",
        "preferredScore": "5.0000",
        "relatedScore": "0.0000",
        "importantSkillBonus": "0.0000",
        "requiredCoverageRate": "0.8000",
        "requiredLevelMatchRate": "0.7000",
        "importantMatchCount": 1,
        "strengths": [
            {
                "skillName": "Java",
                "skillType": "REQUIRED",
                "evaluationType": "LEVEL",
                "userLevel": 2,
                "requiredLevel": 3,
                "matchRate": "0.6667",
                "userImportant": True,
                "requirementSatisfied": False,
            }
        ],
        "gaps": [],
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
                reason="추천 근거",
                strengths="Java 역량",
                weaknesses="AWS 보완 필요",
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
                "reason": "추천 근거",
                "strengths": "Java 역량",
                "weaknesses": "AWS 보완 필요",
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
