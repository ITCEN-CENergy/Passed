from __future__ import annotations

import argparse
import csv
import json
import os
from collections import Counter, defaultdict
from pathlib import Path
from typing import Iterable

import psycopg
from dotenv import load_dotenv
from psycopg.rows import dict_row

EXPORT_FIELDS = [
    "roadmap_id", "skill_priority", "competency_name", "category", "requirement_type",
    "frequency", "milestone_id", "learning_order", "milestone_title", "learning_objective",
    "completion_criteria", "difficulty", "resource_rank", "resource_type", "resource_title",
    "provider", "url", "recommendation_reason", "competency_relevance",
    "milestone_relevance", "difficulty_fit", "accessible", "current", "language",
    "duplicate", "notes",
]

QUERY = """
SELECT r.id AS roadmap_id, rs.priority AS skill_priority,
       rs.standard_competency_name AS competency_name, rs.category,
       rs.requirement_type, rs.frequency, m.id AS milestone_id,
       rm.learning_order, m.title AS milestone_title, m.learning_objective,
       m.completion_criteria, m.difficulty, rr.rank_order AS resource_rank,
       lr.resource_type, lr.title AS resource_title, lr.provider, lr.url,
       rr.recommendation_reason
FROM roadmaps r
JOIN roadmap_skills rs ON rs.roadmap_id = r.id
JOIN roadmap_milestones rm ON rm.roadmap_skill_id = rs.id
JOIN milestones m ON m.id = rm.milestone_id
JOIN resource_recommendations rr ON rr.milestone_id = m.id
JOIN learning_resources lr ON lr.id = rr.resource_id
WHERE r.id = %s
ORDER BY rs.priority, rm.learning_order, rr.rank_order, rr.id
"""


def select_sample(
    rows: Iterable[dict[str, object]],
    competency_limit: int = 10,
    resources_per_competency: int = 3,
) -> list[dict[str, object]]:
    selected: list[dict[str, object]] = []
    competency_order: list[str] = []
    counts: Counter[str] = Counter()
    seen_urls: defaultdict[str, set[str]] = defaultdict(set)
    for row in rows:
        competency = str(row["competency_name"])
        if competency not in competency_order:
            if len(competency_order) >= competency_limit:
                continue
            competency_order.append(competency)
        if counts[competency] >= resources_per_competency:
            continue
        normalized_url = str(row.get("url") or "").rstrip("/").casefold()
        if normalized_url and normalized_url in seen_urls[competency]:
            continue
        if normalized_url:
            seen_urls[competency].add(normalized_url)
        selected.append(row)
        counts[competency] += 1
    return selected


def export_sample(
    database_url: str,
    output: Path,
    roadmap_id: int | None,
    competency_limit: int,
    resources_per_competency: int,
) -> int:
    with psycopg.connect(database_url, row_factory=dict_row) as connection:
        with connection.cursor() as cursor:
            if roadmap_id is None:
                cursor.execute(
                    "SELECT max(id) AS id FROM roadmaps "
                    "WHERE status IN ('ACTIVE', 'COMPLETED')"
                )
                roadmap_id = cursor.fetchone()["id"]
            if roadmap_id is None:
                raise RuntimeError("평가할 완료 로드맵이 없습니다.")
            cursor.execute(QUERY, (roadmap_id,))
            rows = [dict(row) for row in cursor.fetchall()]

    sample = select_sample(rows, competency_limit, resources_per_competency)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=EXPORT_FIELDS, extrasaction="ignore")
        writer.writeheader()
        for row in sample:
            writer.writerow(row)
    return len(sample)


def _score(value: str, field: str) -> int:
    try:
        score = int(value)
    except ValueError as exception:
        raise ValueError(f"{field}는 0, 1, 2 중 하나여야 합니다: {value!r}") from exception
    if score not in {0, 1, 2}:
        raise ValueError(f"{field}는 0, 1, 2 중 하나여야 합니다: {score}")
    return score


def summarize(rows: Iterable[dict[str, str]]) -> dict[str, object]:
    labeled = [row for row in rows if row.get("competency_relevance", "").strip()]
    if not labeled:
        raise ValueError("라벨링된 평가 행이 없습니다.")
    acceptable = irrelevant = duplicates = accessible_count = 0
    language_counts: Counter[str] = Counter()
    provider_counts: Counter[str] = Counter()
    competency_results: defaultdict[str, list[bool]] = defaultdict(list)
    for row in labeled:
        competency_score = _score(row["competency_relevance"], "competency_relevance")
        milestone_score = _score(row["milestone_relevance"], "milestone_relevance")
        _score(row["difficulty_fit"], "difficulty_fit")
        accessible = row.get("accessible", "").strip().lower() == "yes"
        is_duplicate = row.get("duplicate", "").strip().lower() == "yes"
        is_acceptable = competency_score >= 1 and milestone_score >= 1 and accessible
        acceptable += is_acceptable
        irrelevant += competency_score == 0 or milestone_score == 0
        duplicates += is_duplicate
        accessible_count += accessible
        language_counts[row.get("language", "unknown").strip().lower() or "unknown"] += 1
        provider_counts[row.get("provider", "unknown").strip() or "unknown"] += 1
        competency_results[row["competency_name"]].append(is_acceptable)
    total = len(labeled)
    return {
        "evaluated_count": total,
        "acceptable_rate": round(acceptable / total, 4),
        "irrelevant_rate": round(irrelevant / total, 4),
        "accessible_rate": round(accessible_count / total, 4),
        "duplicate_rate": round(duplicates / total, 4),
        "language_distribution": dict(language_counts),
        "provider_distribution": dict(provider_counts),
        "acceptable_rate_by_competency": {
            name: round(sum(values) / len(values), 4)
            for name, values in competency_results.items()
        },
    }


def summarize_file(input_path: Path, output_path: Path | None) -> dict[str, object]:
    with input_path.open(encoding="utf-8-sig", newline="") as file:
        report = summarize(csv.DictReader(file))
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    if output_path:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(rendered + "\n", encoding="utf-8")
    return report


def main() -> None:
    load_dotenv()
    parser = argparse.ArgumentParser(description="학습자료 추천 오프라인 품질 평가")
    commands = parser.add_subparsers(dest="command", required=True)
    export_parser = commands.add_parser("export", help="DB의 기존 추천에서 평가 표본 추출")
    export_parser.add_argument("--output", type=Path, required=True)
    export_parser.add_argument("--roadmap-id", type=int)
    export_parser.add_argument("--competencies", type=int, default=10)
    export_parser.add_argument("--resources-per-competency", type=int, default=3)
    export_parser.add_argument("--database-url", default=os.getenv("DATABASE_URL"))
    summary_parser = commands.add_parser("summarize", help="라벨링 CSV 지표 계산")
    summary_parser.add_argument("--input", type=Path, required=True)
    summary_parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.command == "export":
        if not args.database_url:
            parser.error("--database-url 또는 DATABASE_URL이 필요합니다.")
        count = export_sample(
            args.database_url, args.output, args.roadmap_id,
            args.competencies, args.resources_per_competency,
        )
        print(f"평가 표본 {count}개를 {args.output}에 저장했습니다.")
    else:
        print(json.dumps(summarize_file(args.input, args.output), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
