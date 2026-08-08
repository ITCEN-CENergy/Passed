"""추출 후보와 skills 마스터 매핑 골든셋의 데이터 계약."""

from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, ConfigDict, Field, model_validator

from .skill_extraction_models import SkillCategory


class MappingMethod(str, Enum):
    EXACT = "EXACT"
    NORMALIZED = "NORMALIZED"
    ALIAS = "ALIAS"
    EMBEDDING = "EMBEDDING"


class MappingExpectation(str, Enum):
    MAP = "MAP"
    MASTER_GAP = "MASTER_GAP"
    NO_MATCH = "NO_MATCH"


class SkillMappingGoldenCase(BaseModel):
    """후보 하나가 어떤 마스터 스킬로 연결되어야 하는지 나타낸 정답."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    case_id: str = Field(min_length=1)
    extracted_name: str = Field(min_length=1, max_length=100)
    extracted_category: SkillCategory
    expectation: MappingExpectation
    expected_skill_name: str | None = None
    expected_skill_category: SkillCategory | None = None
    allowed_mapping_methods: list[MappingMethod] = Field(default_factory=list)
    rationale: str = Field(min_length=1)

    @model_validator(mode="after")
    def validate_mapping_expectation(self) -> "SkillMappingGoldenCase":
        # Q. 매핑 실패 정답에도 expected_skill_name을 적으면 안 되나요?
        # A. should_map=false는 현재 마스터에 적절한 행이 없다는 뜻입니다. 기대 이름까지
        #    있으면 평가기가 성공과 실패 중 무엇을 정답으로 봐야 하는지 모호해집니다.
        has_expected_target = bool(
            self.expected_skill_name and self.expected_skill_category
        )
        should_map = self.expectation is MappingExpectation.MAP
        if should_map and not has_expected_target:
            raise ValueError("MAP에는 기대 마스터 이름과 카테고리가 필요합니다.")
        if should_map and not self.allowed_mapping_methods:
            raise ValueError("MAP에는 허용 매핑 방식이 하나 이상 필요합니다.")
        if not should_map and (
            self.expected_skill_name is not None
            or self.expected_skill_category is not None
            or self.allowed_mapping_methods
        ):
            raise ValueError(
                "MASTER_GAP/NO_MATCH에는 기대 마스터와 매핑 방식을 둘 수 없습니다."
            )
        return self

    @property
    def should_map(self) -> bool:
        """기존 평가 코드가 의미를 잃지 않고 MAP 여부를 읽게 하는 편의 속성."""
        return self.expectation is MappingExpectation.MAP


def load_mapping_golden_set(path: "Path") -> list[SkillMappingGoldenCase]:
    """JSON 배열을 엄격한 매핑 골든셋 모델로 읽는다."""
    import json
    from pathlib import Path

    resolved = Path(path)
    raw = json.loads(resolved.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise ValueError(f"JSON 최상위 값은 배열이어야 합니다: {resolved}")
    cases = [SkillMappingGoldenCase.model_validate(item) for item in raw]
    case_ids = [case.case_id for case in cases]
    if len(case_ids) != len(set(case_ids)):
        raise ValueError("매핑 골든셋 case_id가 중복되었습니다.")
    return cases
