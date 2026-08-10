import os

from fastapi.testclient import TestClient

os.environ["ROADMAP_GENERATOR"] = "fake"

from main import app


client = TestClient(app)


def payload() -> dict:
    return {
        "roadmapId": 945,
        "title": "JavaScript 학습 로드맵",
        "delayDays": 4,
        "userInstruction": "필수 실습 중심으로 줄여줘",
        "milestones": [
            {
                "milestoneId": 819,
                "roadmapSkillId": 789,
                "title": "JavaScript 기본 문법",
                "status": "NOT_STARTED",
                "estimatedMinutes": 180,
                "learningOrder": 1,
                "required": True,
            }
        ],
    }


def test_replan_returns_one_safe_decision_per_milestone() -> None:
    response = client.post("/api/v1/roadmaps/replan", json=payload())

    assert response.status_code == 200
    body = response.json()
    assert body["decisions"] == [{
        "milestoneId": 819,
        "action": "KEEP",
        "learningOrder": 1,
        "reason": "현재 학습 계획과 진행 상태를 안전하게 유지합니다.",
    }]


def test_replan_rejects_duplicate_milestone_ids() -> None:
    request = payload()
    request["milestones"].append(dict(request["milestones"][0]))

    response = client.post("/api/v1/roadmaps/replan", json=request)

    assert response.status_code == 422
