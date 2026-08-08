from fastapi.testclient import TestClient

from api.features.skill_gap import router as skill_gap_router
from api.features.skill_gap.schema import (
    LearningCompetencyItem,
    LearningCompetencyResponse,
)
from app.main import app


client = TestClient(app)


def test_learning_competency_endpoint_keeps_existing_contract(monkeypatch):
    monkeypatch.setattr(
        skill_gap_router,
        "analyze_learning_competencies",
        lambda user_id, job_posting_id: LearningCompetencyResponse(
            user_id=user_id,
            job_posting_id=job_posting_id,
            competencies=[
                LearningCompetencyItem(
                    standard_competency_id=10,
                    standard_competency_name="Docker",
                    category="TECHNICAL_SKILL",
                    requirement_type="REQUIRED",
                    current_level=1,
                    target_level=3,
                    current_level_evidence="Docker local usage",
                )
            ],
        ),
    )

    response = client.post(
        "/api/v1/skill-gaps/learning-competencies",
        json={"userId": 257, "jobPostingId": 101},
    )

    assert response.status_code == 200
    assert response.json() == {
        "userId": 257,
        "jobPostingId": 101,
        "competencies": [
            {
                "standardCompetencyId": 10,
                "standardCompetencyName": "Docker",
                "category": "TECHNICAL_SKILL",
                "requirementType": "REQUIRED",
                "currentLevel": 1,
                "targetLevel": 3,
                "currentLevelEvidence": "Docker local usage",
            }
        ],
    }


def test_learning_competency_endpoint_rejects_invalid_ids():
    response = client.post(
        "/api/v1/skill-gaps/learning-competencies",
        json={"userId": 0, "jobPostingId": 101},
    )

    assert response.status_code == 422
