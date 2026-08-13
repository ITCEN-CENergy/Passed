import os

from fastapi.testclient import TestClient

os.environ["ROADMAP_GENERATOR"] = "fake"
os.environ["ROADMAP_RESOURCE_SEARCH_ENABLED"] = "false"

from app.main import app


client = TestClient(app)


def payload() -> dict:
    return {
        "roadmapId": 945,
        "title": "JavaScript 학습 로드맵",
        "userInstruction": "필수 실습 중심으로 줄여줘",
        "groups": [
            {
                "groupKey": "skill-789-group-1",
                "roadmapSkillId": 789,
                "standardCompetencyId": 96,
                "skillName": "JavaScript",
                "category": "TECHNICAL_SKILL",
                "currentLevel": 1,
                "targetLevel": 3,
                "assignedEstimatedMinutes": 120,
                "sourceMilestones": [
                    {
                        "title": "JavaScript 기본 문법",
                        "estimatedMinutes": 180,
                        "description": "핵심 문법을 실습합니다.",
                        "learningObjective": "핵심 문법을 사용할 수 있다.",
                        "completionCriteria": "실행 가능한 코드를 완성한다.",
                        "startLevel": 1,
                        "targetLevel": 2,
                        "milestoneType": "PRACTICE",
                        "difficulty": "BEGINNER",
                    }
                ],
            }
        ],
    }


def test_replan_returns_content_for_backend_defined_group() -> None:
    response = client.post("/api/v1/roadmaps/replan", json=payload())

    assert response.status_code == 200
    body = response.json()
    assert body["groups"][0]["groupKey"] == "skill-789-group-1"
    assert body["groups"][0]["title"]


def test_replan_accepts_zero_current_level() -> None:
    request = payload()
    request["groups"][0]["currentLevel"] = 0
    request["groups"][0]["sourceMilestones"][0]["startLevel"] = 0

    response = client.post("/api/v1/roadmaps/replan", json=request)

    assert response.status_code == 200


def test_replan_rejects_duplicate_group_keys() -> None:
    request = payload()
    request["groups"].append(dict(request["groups"][0]))

    response = client.post("/api/v1/roadmaps/replan", json=request)

    assert response.status_code == 422
