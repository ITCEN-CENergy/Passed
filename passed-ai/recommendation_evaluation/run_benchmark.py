from __future__ import annotations

import argparse
import csv
import json
import math
from collections import defaultdict
from dataclasses import asdict, dataclass
from pathlib import Path


TOP_K = 10
REQUIRED_MAX_SCORE = 60.0
PREFERRED_MAX_SCORE = 20.0
RELATED_MAX_SCORE = 10.0
IMPORTANT_BONUS_MAX_SCORE = 10.0
REQUIRED_COVERAGE_THRESHOLD = 0.5


@dataclass(frozen=True)
class RankedCase:
    profile_id: str
    target_job_role: str
    candidate_id: str
    candidate_title: str
    scenario: str
    relevance: int
    variant: str
    rank: int
    total_score: float
    required_coverage: float
    grade: str
    is_accurate_recommendation: bool
    is_over_recommendation: bool


def _grade(total_score: float, coverage: float) -> tuple[int, str]:
    if total_score >= 80.0 and coverage >= 0.8:
        return 4, "매우 적합"
    if total_score >= 65.0 and coverage >= 0.6:
        return 3, "적합"
    if total_score >= 50.0 and coverage >= 0.4:
        return 2, "도전 가능"
    return 1, "적합도 낮음"


def _score(row: dict[str, str], variant: str) -> tuple[int, str, float, float]:
    required_total = int(row["required_skill_count"])
    exact = int(row["exact_required_match_count"])
    added_key = (
        "legacy_added_required_count"
        if variant == "개선 전"
        else "verified_added_required_count"
    )
    effective_required = min(required_total, exact + int(row[added_key]))
    coverage = effective_required / required_total if required_total else 1.0
    preferred_rate = int(row["preferred_match_count"]) / int(row["preferred_skill_count"])
    related_rate = int(row["related_match_count"]) / int(row["related_skill_count"])
    important_rate = int(row["important_match_count"]) / int(row["important_skill_count"])
    total = (
        REQUIRED_MAX_SCORE * coverage
        + PREFERRED_MAX_SCORE * preferred_rate
        + RELATED_MAX_SCORE * related_rate
        + IMPORTANT_BONUS_MAX_SCORE * important_rate
    )
    grade_priority, grade = _grade(total, coverage)
    return grade_priority, grade, round(total, 4), round(coverage, 4)


def _dcg(relevances: list[int]) -> float:
    return sum((2**value - 1) / math.log2(index + 2) for index, value in enumerate(relevances))


def evaluate(rows: list[dict[str, str]]) -> tuple[list[dict[str, object]], list[dict[str, object]], list[RankedCase]]:
    by_profile: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        by_profile[row["profile_id"]].append(row)

    profile_metrics: list[dict[str, object]] = []
    details: list[RankedCase] = []
    for profile_id, cases in sorted(by_profile.items()):
        relevant_total = sum(int(case["ground_truth_relevance"]) == 2 for case in cases)
        ideal_relevances = sorted(
            (int(case["ground_truth_relevance"]) for case in cases),
            reverse=True,
        )[:TOP_K]
        ideal_dcg = _dcg(ideal_relevances)
        for variant in ("개선 전", "개선 후"):
            scored = []
            for case in cases:
                grade_priority, grade, total_score, coverage = _score(case, variant)
                if coverage >= REQUIRED_COVERAGE_THRESHOLD:
                    scored.append((grade_priority, total_score, coverage, case, grade))
            scored.sort(
                key=lambda item: (
                    -item[0],
                    -item[1],
                    -item[2],
                    item[3]["candidate_id"],
                )
            )
            top = scored[:TOP_K]
            top_relevances = [int(item[3]["ground_truth_relevance"]) for item in top]
            accurate_count = sum(value == 2 for value in top_relevances)
            over_count = sum(value == 0 for value in top_relevances)
            precision = accurate_count / TOP_K
            over_rate = over_count / TOP_K
            recall = accurate_count / relevant_total if relevant_total else 0.0
            ndcg = _dcg(top_relevances) / ideal_dcg if ideal_dcg else 0.0
            profile_metrics.append(
                {
                    "profile_id": profile_id,
                    "target_job_role": cases[0]["target_job_role"],
                    "variant": variant,
                    "candidate_count": len(cases),
                    "top_k": TOP_K,
                    "precision_at_10": round(precision, 4),
                    "ndcg_at_10": round(ndcg, 4),
                    "over_recommendation_rate_at_10": round(over_rate, 4),
                    "recall_at_10": round(recall, 4),
                }
            )
            for rank, (_, total_score, coverage, case, grade) in enumerate(top, start=1):
                relevance = int(case["ground_truth_relevance"])
                details.append(
                    RankedCase(
                        profile_id=profile_id,
                        target_job_role=case["target_job_role"],
                        candidate_id=case["candidate_id"],
                        candidate_title=case["candidate_title"],
                        scenario=case["scenario"],
                        relevance=relevance,
                        variant=variant,
                        rank=rank,
                        total_score=total_score,
                        required_coverage=coverage,
                        grade=grade,
                        is_accurate_recommendation=relevance == 2,
                        is_over_recommendation=relevance == 0,
                    )
                )

    aggregate_metrics: list[dict[str, object]] = []
    for variant in ("개선 전", "개선 후"):
        selected = [row for row in profile_metrics if row["variant"] == variant]
        for metric, display_name in (
            ("precision_at_10", "추천 정확도 (Precision@10)"),
            ("ndcg_at_10", "순위 품질 (NDCG@10)"),
            ("over_recommendation_rate_at_10", "과대 추천률@10"),
            ("recall_at_10", "적합 공고 회수율 (Recall@10)"),
        ):
            values = [float(row[metric]) for row in selected]
            aggregate_metrics.append(
                {
                    "variant": variant,
                    "metric": metric,
                    "metric_name": display_name,
                    "value": round(sum(values) / len(values), 4),
                    "profile_count": len(values),
                    "candidate_pair_count": len(rows),
                }
            )
    return aggregate_metrics, profile_metrics, details


def _read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def _write_csv(path: Path, rows: list[dict[str, object]], fieldnames: list[str] | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    resolved_fieldnames = fieldnames or list(rows[0].keys())
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=resolved_fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    base = Path(__file__).parent
    parser = argparse.ArgumentParser(description="Evaluate recommendation accuracy before and after hybrid verification")
    parser.add_argument("--dataset", type=Path, default=base / "data" / "recommendation_benchmark.csv")
    parser.add_argument("--output-dir", type=Path, default=base / "results")
    args = parser.parse_args()

    rows = _read_csv(args.dataset)
    aggregate, profiles, details = evaluate(rows)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    _write_csv(args.output_dir / "aggregate_metrics.csv", aggregate)
    _write_csv(args.output_dir / "profile_metrics.csv", profiles)
    _write_csv(
        args.output_dir / "top10_recommendation_details.csv",
        [asdict(detail) for detail in details],
    )
    payload = {
        "benchmark": {
            "profile_count": len({row["profile_id"] for row in rows}),
            "candidate_pair_count": len(rows),
            "candidates_per_profile": 30,
            "top_k": TOP_K,
            "relevance_definition": {
                "2": "적합: 동일 직무이며 필수역량 충족 또는 직접 수행 근거 확인",
                "1": "도전 가능: 동일 직무이나 숙련도/필수역량 일부 부족",
                "0": "부적합: 직무 불일치 또는 직접 수행 근거 없음",
            },
        },
        "aggregate_metrics": aggregate,
        "profile_metrics": profiles,
    }
    (args.output_dir / "benchmark_result.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(payload["aggregate_metrics"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
