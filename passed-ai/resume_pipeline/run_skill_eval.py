"""사전에 생성한 스킬 후보 예측 JSON의 Precision/Recall/F1 CLI."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import sys

from .skill_extraction_eval import (
    evaluate_skill_predictions,
    generate_golden_predictions,
    load_golden_set,
    load_predictions,
)
from .skill_extraction_prompt import SYSTEM_PROMPT
from .skill_extraction_worker import (
    _EXPLICIT_COMPLETED_SKILL_RULES,
    SKILL_EXTRACTION_MODEL,
    create_skill_extraction_client,
)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="스킬 후보 추출 골든셋 평가")
    parser.add_argument("--golden", type=Path, required=True)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--predictions", type=Path, help="기존 예측 JSON")
    source.add_argument(
        "--generate",
        action="store_true",
        help="OpenAI로 골든셋 예측을 생성한 뒤 즉시 평가",
    )
    parser.add_argument(
        "--save-predictions",
        type=Path,
        help="--generate 결과를 저장할 JSON 경로",
    )
    parser.add_argument(
        "--save-report",
        type=Path,
        help="평가지표와 재현용 메타데이터를 저장할 JSON 경로",
    )
    parser.add_argument(
        "--model-label",
        help="기존 --predictions를 평가할 때 메타데이터에 기록할 모델명",
    )
    parser.add_argument(
        "--disable-recovery-rule",
        action="append",
        default=[],
        metavar="SKILL_NAME",
        help=(
            "평가에서 제외할 deterministic recovery 스킬명. 여러 번 지정할 수 "
            "있으며 운영 API 기본 동작에는 영향을 주지 않습니다."
        ),
    )
    parser.add_argument(
        "--disable-all-recovery-rules",
        action="store_true",
        help="모든 deterministic recovery 규칙을 끈 counterfactual 평가",
    )
    args = parser.parse_args(argv)

    available_rules = {
        name for name, _category, _pattern in _EXPLICIT_COMPLETED_SKILL_RULES
    }
    disabled_rules = frozenset(
        available_rules if args.disable_all_recovery_rules else args.disable_recovery_rule
    )
    unknown_rules = disabled_rules - available_rules
    if unknown_rules:
        parser.error(f"존재하지 않는 recovery 규칙입니다: {sorted(unknown_rules)}")

    golden = load_golden_set(args.golden)
    if args.generate:
        if not os.getenv("OPENAI_API_KEY"):
            parser.error("--generate에는 OPENAI_API_KEY가 필요합니다.")
        predictions = generate_golden_predictions(
            golden,
            client=create_skill_extraction_client(),
            disabled_recovery_rules=disabled_rules,
        )
        if args.save_predictions:
            args.save_predictions.parent.mkdir(parents=True, exist_ok=True)
            prediction_json = "[\n" + ",\n".join(
                item.model_dump_json(indent=2) for item in predictions
            ) + "\n]\n"
            args.save_predictions.write_text(prediction_json, encoding="utf-8")
    else:
        predictions = load_predictions(args.predictions)

    report = evaluate_skill_predictions(golden, predictions)
    print(report.model_dump_json(indent=2))
    if args.save_report:
        args.save_report.parent.mkdir(parents=True, exist_ok=True)
        # Q. prompt hash 외에 규칙 hash도 기록하는 이유는 무엇인가요?
        # A. 현재 후처리 규칙도 최종 예측을 바꾸므로 프롬프트만 같다고 같은
        #    파이프라인이 아닙니다. 두 계약을 함께 고정해야 회귀 결과를 재현할 수 있습니다.
        rule_contract = [
            {
                "name": name,
                "category": category.value,
                "pattern": pattern.pattern,
                "flags": pattern.flags,
                "enabled": name not in disabled_rules,
            }
            for name, category, pattern in _EXPLICIT_COMPLETED_SKILL_RULES
        ]
        rules_json = json.dumps(
            rule_contract,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )
        prompt_hash = hashlib.sha256(SYSTEM_PROMPT.encode("utf-8")).hexdigest()
        rules_hash = hashlib.sha256(rules_json.encode("utf-8")).hexdigest()
        artifact = {
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "model": (
                SKILL_EXTRACTION_MODEL if args.generate else args.model_label
            ),
            "golden_file": str(args.golden),
            "golden_sha256": hashlib.sha256(args.golden.read_bytes()).hexdigest(),
            "prompt_sha256": prompt_hash,
            "recovery_rules_sha256": rules_hash,
            "pipeline_sha256": hashlib.sha256(
                f"{prompt_hash}:{rules_hash}".encode("utf-8")
            ).hexdigest(),
            "disabled_recovery_rules": sorted(disabled_rules),
            "metrics": report.model_dump(mode="json"),
        }
        args.save_report.write_text(
            json.dumps(artifact, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
