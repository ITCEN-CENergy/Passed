from copy import deepcopy

import pytest
from fastapi.testclient import TestClient

from main import app


client = TestClient(app)


def competency(
    *,
    key: str = "competency-1",
    name: str = "Docker",
    category: str = "TECHNICAL_SKILL",
    current_level: int = 1,
    target_level: int = 3,
) -> dict:
    return {
        "roadmapSkillKey": key,
        "standardCompetencyId": 1,
        "standardCompetencyName": name,
        "category": category,
        "currentLevel": current_level,
        "targetLevel": target_level,
        "requirementType": "REQUIRED",
        "gapLevel": max(target_level - current_level, 0),
        "frequency": 2,
        "priority": 1,
        "sources": [
            {
                "jobPostingId": 101,
                "currentEvidence": f"{name} 학습 및 실습 경험",
            }
        ],
    }


def request_with(*competencies: dict) -> dict:
    return {"userId": 10, "competencies": list(competencies)}


def test_generate_normal_request() -> None:
    response = client.post(
        "/api/v1/roadmaps/generate", json=request_with(competency())
    )

    assert response.status_code == 200
    body = response.json()
    assert body["title"] == "개인 맞춤 역량 강화 로드맵"
    assert body["skills"][0]["roadmapSkillKey"] == "competency-1"
    assert len(body["skills"][0]["milestones"]) == 2


def test_generate_multiple_competencies_in_request_order() -> None:
    payload = request_with(
        competency(key="docker", current_level=1, target_level=3),
        competency(key="aws", name="AWS", current_level=0, target_level=1),
    )

    response = client.post("/api/v1/roadmaps/generate", json=payload)

    assert response.status_code == 200
    assert [skill["roadmapSkillKey"] for skill in response.json()["skills"]] == [
        "docker",
        "aws",
    ]


@pytest.mark.parametrize(
    ("current_level", "target_level", "expected_pairs"),
    [
        (0, 3, [(0, 1), (1, 2), (2, 3)]),
        (1, 3, [(1, 2), (2, 3)]),
    ],
)
def test_milestones_connect_each_level(
    current_level: int, target_level: int, expected_pairs: list[tuple[int, int]]
) -> None:
    payload = request_with(
        competency(current_level=current_level, target_level=target_level)
    )

    response = client.post("/api/v1/roadmaps/generate", json=payload)

    milestones = response.json()["skills"][0]["milestones"]
    assert [(item["startLevel"], item["targetLevel"]) for item in milestones] == expected_pairs
    assert [item["learningOrder"] for item in milestones] == list(
        range(1, len(milestones) + 1)
    )
    assert all(item["estimatedMinutes"] >= 1 for item in milestones)


def test_general_milestone_type_difficulty_and_time_are_deterministic() -> None:
    payload = request_with(competency(current_level=0, target_level=3))

    milestones = client.post(
        "/api/v1/roadmaps/generate", json=payload
    ).json()["skills"][0]["milestones"]

    assert [item["milestoneType"] for item in milestones] == [
        "CONCEPT",
        "PRACTICE",
        "PROJECT",
    ]
    assert [item["difficulty"] for item in milestones] == [
        "BEGINNER",
        "INTERMEDIATE",
        "ADVANCED",
    ]
    assert [item["estimatedMinutes"] for item in milestones] == [60, 120, 180]


def test_certification_always_has_one_fixed_milestone() -> None:
    payload = request_with(
        competency(
            key="certification-1",
            name="SQLD",
            category="CERTIFICATION",
            current_level=1,
            target_level=1,
        )
    )

    response = client.post("/api/v1/roadmaps/generate", json=payload)

    assert response.status_code == 200
    milestones = response.json()["skills"][0]["milestones"]
    assert len(milestones) == 1
    assert milestones[0]["startLevel"] == 0
    assert milestones[0]["targetLevel"] == 1
    assert milestones[0]["milestoneType"] == "CERTIFICATION"
    assert milestones[0]["learningOrder"] == 1


def test_same_request_returns_same_response() -> None:
    payload = request_with(competency(current_level=0, target_level=3))

    first = client.post("/api/v1/roadmaps/generate", json=deepcopy(payload))
    second = client.post("/api/v1/roadmaps/generate", json=deepcopy(payload))

    assert first.status_code == second.status_code == 200
    assert first.json() == second.json()


def test_nullable_current_evidence_is_accepted() -> None:
    item = competency()
    item["sources"][0]["currentEvidence"] = None

    response = client.post("/api/v1/roadmaps/generate", json=request_with(item))

    assert response.status_code == 200


@pytest.mark.parametrize(
    "payload",
    [
        {"userId": 10, "competencies": []},
        request_with(competency(current_level=3, target_level=1)),
        request_with(competency(current_level=-1, target_level=1)),
        request_with(competency(current_level=0, target_level=6)),
        request_with(competency(name="   ")),
    ],
)
def test_invalid_request_returns_422(payload: dict) -> None:
    response = client.post("/api/v1/roadmaps/generate", json=payload)

    assert response.status_code == 422


def test_router_is_registered() -> None:
    paths = app.openapi()["paths"]

    assert "/api/v1/roadmaps/generate" in paths
