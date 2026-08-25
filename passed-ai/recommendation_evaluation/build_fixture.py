from __future__ import annotations

import argparse
import csv
from pathlib import Path


ROLES: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("백엔드 개발자", ("Java", "Spring Boot", "SQL", "REST API", "Docker", "Redis")),
    ("프론트엔드 개발자", ("JavaScript", "React", "TypeScript", "CSS", "Jest", "웹 접근성")),
    ("데이터 분석가", ("SQL", "Python", "통계 분석", "Tableau", "A/B 테스트", "데이터 시각화")),
    ("머신러닝 엔지니어", ("Python", "PyTorch", "머신러닝", "MLOps", "Docker", "모델 평가")),
    ("DevOps 엔지니어", ("Linux", "Docker", "Kubernetes", "CI/CD", "AWS", "모니터링")),
    ("정보보안 엔지니어", ("보안", "접근 제어", "취약점 분석", "IDS/IPS", "ISMS 대응", "로그 분석")),
    ("프로덕트 매니저", ("요구사항 분석", "제품 지표", "A/B 테스트", "로드맵 수립", "Jira", "사용자 조사")),
    ("UI/UX 디자이너", ("Figma", "사용자 조사", "프로토타이핑", "디자인 시스템", "웹 접근성", "사용성 테스트")),
    ("QA 자동화 엔지니어", ("테스트 자동화", "API 테스트", "Selenium", "Jest", "CI/CD", "결함 분석")),
    ("클라우드 엔지니어", ("AWS", "Terraform", "Kubernetes", "네트워크", "CloudWatch", "FinOps")),
)

SCENARIO_COUNTS = {
    "STRONG_EXACT": 8,
    "HIDDEN_DIRECT": 8,
    "BORDERLINE": 6,
    "SEMANTIC_TRAP": 6,
    "OUT_OF_SCOPE": 2,
}

LABELS = {
    "STRONG_EXACT": (2, "적합", "동일 직무이며 필수 역량 75% 이상을 직접 보유"),
    "HIDDEN_DIRECT": (2, "적합", "동일 직무이며 원문에서 누락 필수 역량의 직접 수행이 확인됨"),
    "BORDERLINE": (1, "도전 가능", "동일 직무이나 숙련도 또는 필수 역량 한 가지가 부족"),
    "SEMANTIC_TRAP": (0, "부적합", "유사 용어만 있고 목표 역량의 직접 수행 근거가 없음"),
    "OUT_OF_SCOPE": (0, "부적합", "직무 범위와 핵심 필수 역량이 모두 불일치"),
}

FIELDNAMES = (
    "case_id",
    "profile_id",
    "target_job_role",
    "candidate_id",
    "candidate_title",
    "scenario",
    "ground_truth_relevance",
    "ground_truth_label",
    "label_reason",
    "required_skills",
    "exact_matched_skills",
    "legacy_semantic_added_skills",
    "verified_evidence_added_skills",
    "evidence_summary",
    "required_skill_count",
    "exact_required_match_count",
    "legacy_added_required_count",
    "verified_added_required_count",
    "preferred_skill_count",
    "preferred_match_count",
    "related_skill_count",
    "related_match_count",
    "important_skill_count",
    "important_match_count",
)


def _scenario(candidate_index: int) -> tuple[str, int]:
    offset = candidate_index
    for name, count in SCENARIO_COUNTS.items():
        if offset < count:
            return name, offset
        offset -= count
    raise AssertionError("candidate index out of range")


def _counts(role_index: int, candidate_index: int, scenario: str, local_index: int) -> dict[str, int]:
    if scenario == "STRONG_EXACT":
        exact = 3 + (local_index % 2)
        return {
            "exact": exact,
            "legacy": 1 if exact == 3 else 0,
            "verified": 1 if exact == 3 else 0,
            "preferred": 1 + (local_index % 2),
            "related": 1 + (local_index % 3 == 0),
            "important": 1,
        }
    if scenario == "HIDDEN_DIRECT":
        return {
            "exact": 2,
            "legacy": 2,
            "verified": 1 if (candidate_index + role_index) % 3 == 0 else 2,
            "preferred": 1 + (local_index % 2),
            "related": 1,
            "important": int(local_index % 3 > 0),
        }
    if scenario == "BORDERLINE":
        return {
            "exact": 2,
            "legacy": 1,
            # Direct-document fallback can recover evidence that legacy
            # skill-to-skill expansion never retrieved.
            "verified": 2 if local_index == role_index % 6 else int((candidate_index + role_index) % 2 == 1),
            "preferred": 2,
            "related": 2,
            "important": 1,
        }
    if scenario == "SEMANTIC_TRAP":
        rare_false_positive = role_index in {2, 7} and local_index == role_index % 6
        return {
            "exact": 1,
            "legacy": 3,
            "verified": 3 if rare_false_positive else int((candidate_index + role_index) % 5 == 0),
            "preferred": 2,
            "related": 2,
            "important": 1,
        }
    return {
        "exact": 0,
        "legacy": 2,
        "verified": 0,
        "preferred": 2,
        "related": 2,
        "important": 1,
    }


def _rotated(values: tuple[str, ...], start: int, count: int) -> list[str]:
    if count <= 0:
        return []
    return [values[(start + index) % len(values)] for index in range(count)]


def build_rows() -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    case_number = 1
    for role_index, (role, skills) in enumerate(ROLES):
        for candidate_index in range(30):
            scenario, local_index = _scenario(candidate_index)
            counts = _counts(role_index, candidate_index, scenario, local_index)
            relevance, label, reason = LABELS[scenario]
            required = _rotated(skills, candidate_index % len(skills), 4)
            exact = required[: counts["exact"]]
            missing = required[counts["exact"] :]
            legacy = missing[: counts["legacy"]]
            verified = missing[: counts["verified"]]
            if counts["verified"] > len(missing):
                verified += _rotated(skills, candidate_index + 4, counts["verified"] - len(missing))
            evidence_summary = {
                "STRONG_EXACT": "정규화된 동일 스킬 ID와 수행 수준이 확인됨",
                "HIDDEN_DIRECT": "이력서 원문에 목표 역량을 적용·구현한 완료 행동이 있음",
                "BORDERLINE": "관련 수행은 있으나 요구 수준 또는 범위가 일부 부족",
                "SEMANTIC_TRAP": "관련 단어는 있으나 목표 역량의 직접 수행 문장이 없음",
                "OUT_OF_SCOPE": "목표 직무와 연결되는 직접 근거가 없음",
            }[scenario]
            rows.append(
                {
                    "case_id": f"RC-{case_number:03d}",
                    "profile_id": f"P-{role_index + 1:02d}",
                    "target_job_role": role,
                    "candidate_id": f"{role_index + 1:02d}-{candidate_index + 1:02d}",
                    "candidate_title": f"{role} 채용 {candidate_index + 1:02d}",
                    "scenario": scenario,
                    "ground_truth_relevance": relevance,
                    "ground_truth_label": label,
                    "label_reason": reason,
                    "required_skills": " | ".join(required),
                    "exact_matched_skills": " | ".join(exact),
                    "legacy_semantic_added_skills": " | ".join(legacy),
                    "verified_evidence_added_skills": " | ".join(verified),
                    "evidence_summary": evidence_summary,
                    "required_skill_count": 4,
                    "exact_required_match_count": counts["exact"],
                    "legacy_added_required_count": counts["legacy"],
                    "verified_added_required_count": counts["verified"],
                    "preferred_skill_count": 2,
                    "preferred_match_count": counts["preferred"],
                    "related_skill_count": 2,
                    "related_match_count": counts["related"],
                    "important_skill_count": 1,
                    "important_match_count": counts["important"],
                }
            )
            case_number += 1
    return rows


def main() -> None:
    parser = argparse.ArgumentParser(description="Build the deterministic recommendation benchmark fixture")
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).parent / "data" / "recommendation_benchmark.csv",
    )
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=FIELDNAMES)
        writer.writeheader()
        writer.writerows(build_rows())
    print(f"wrote {len(build_rows())} rows to {args.output}")


if __name__ == "__main__":
    main()
