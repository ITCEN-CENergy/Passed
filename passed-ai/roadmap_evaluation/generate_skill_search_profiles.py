from __future__ import annotations

import argparse
import json
import math
import re
from collections import Counter
from pathlib import Path

import psycopg
from psycopg.rows import dict_row


CATEGORY_FILES = {
    "TECHNICAL_SKILL": "technical_skill.json",
    "EXPERIENCE": "experience.json",
    "CERTIFICATION": "certification.json",
    "BEHAVIORAL_TRAIT": "behavioral_trait.json",
}
STOP_WORDS = {
    "개발", "관리", "관련", "결과", "기능", "기술", "데이터", "문서", "사용",
    "서비스", "실무", "역량", "위한", "정보", "통해", "학습", "활용",
    "application", "development", "management", "service", "using",
}


def tokens(value: str) -> list[str]:
    return [
        token
        for token in re.findall(r"[가-힣A-Za-z0-9+#.]+", value.casefold())
        if len(token) >= 2 and token not in STOP_WORDS
    ]


def load_existing(directory: Path) -> dict[int, dict[str, object]]:
    profiles: dict[int, dict[str, object]] = {}
    for path in directory.glob("*.json"):
        payload = json.loads(path.read_text(encoding="utf-8"))
        profiles.update({int(skill_id): item for skill_id, item in payload.items()})
    return profiles


def generate(database_url: str, output_directory: Path) -> dict[str, int]:
    with psycopg.connect(database_url, row_factory=dict_row) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT s.id, s.name, s.description, s.category,
                       COALESCE(string_agg(sa.alias, ' ' ORDER BY sa.id)
                                FILTER (WHERE sa.is_active), '') AS aliases
                FROM skills s
                LEFT JOIN skill_aliases sa ON sa.skill_id = s.id
                GROUP BY s.id, s.name, s.description, s.category
                ORDER BY s.id
                """
            )
            rows = list(cursor.fetchall())

    corpus_tokens = {
        int(row["id"]): set(tokens(
            " ".join(filter(None, (row["name"], row["description"], row["aliases"])))
        ))
        for row in rows
    }
    frequencies = Counter(
        token for document in corpus_tokens.values() for token in document
    )
    document_count = max(len(rows), 1)
    existing = load_existing(output_directory)
    grouped: dict[str, dict[str, object]] = {
        filename: {} for filename in CATEGORY_FILES.values()
    }

    for row in rows:
        skill_id = int(row["id"])
        category = str(row["category"])
        filename = CATEGORY_FILES.get(category)
        if filename is None:
            raise ValueError(f"unknown skill category: {category}")
        current = existing.get(skill_id)
        if current and current.get("reviewed") is True:
            profile = current
        else:
            ranked = sorted(
                corpus_tokens[skill_id],
                key=lambda token: (
                    -math.log((document_count + 1) / (frequencies[token] + 1)),
                    token,
                ),
            )
            aliases = str(row["aliases"] or "").split()
            english_terms = [
                term for term in aliases
                if re.search(r"[A-Za-z]", term)
            ]
            name = str(row["name"])
            query = " ".join([name, *ranked[:6]])
            profile = {
                "skillName": name,
                "queries": {
                    "ko": [query],
                    "en": [" ".join(english_terms)] if english_terms else [],
                },
                "excludeTerms": [],
                "reviewed": False,
            }
        grouped[filename][str(skill_id)] = profile

    output_directory.mkdir(parents=True, exist_ok=True)
    for filename, payload in grouped.items():
        ordered = dict(sorted(payload.items(), key=lambda item: int(item[0])))
        (output_directory / filename).write_text(
            json.dumps(ordered, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    return {filename: len(payload) for filename, payload in grouped.items()}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--database-url", required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(
        generate(args.database_url, args.output_directory),
        ensure_ascii=False,
    ))


if __name__ == "__main__":
    main()
