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
    args = parser.parse_args(argv)

    golden = load_golden_set(args.golden)
    if args.generate:
        if not os.getenv("OPENAI_API_KEY"):
            parser.error("--generate에는 OPENAI_API_KEY가 필요합니다.")
        predictions = generate_golden_predictions(
            golden,
            client=create_skill_extraction_client(),
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
        artifact = {
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "model": (
                SKILL_EXTRACTION_MODEL if args.generate else args.model_label
            ),
            "golden_file": str(args.golden),
            "golden_sha256": hashlib.sha256(args.golden.read_bytes()).hexdigest(),
            "prompt_sha256": hashlib.sha256(
                SYSTEM_PROMPT.encode("utf-8")
            ).hexdigest(),
            "metrics": report.model_dump(mode="json"),
        }
        args.save_report.write_text(
            json.dumps(artifact, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
