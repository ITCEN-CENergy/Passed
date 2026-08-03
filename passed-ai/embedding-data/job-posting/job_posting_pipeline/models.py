"""도메인 모델: source_type, 매칭 여부, 청크 데이터 클래스."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class SourceType(str, Enum):
    """청크 소스 유형. 허용값은 계획서 6절 기준(TECH_STACK, ETC 포함)."""

    POSITION_DETAIL = "POSITION_DETAIL"
    MAIN_TASK = "MAIN_TASK"
    REQUIREMENT = "REQUIREMENT"
    PREFERENCE = "PREFERENCE"
    TECH_STACK = "TECH_STACK"
    BENEFIT = "BENEFIT"
    PROCESS = "PROCESS"
    DISQUALIFICATION = "DISQUALIFICATION"
    ETC = "ETC"


# 추천에 사용하면 안 되는 설명성·행정성 청크 유형이다.
# 계획서 7절 매칭 규칙:
#   PROCESS, DISQUALIFICATION, BENEFIT -> use_for_matching = false
#   나머지 source_type                -> use_for_matching = true
NON_MATCHING_SOURCE_TYPES: frozenset[SourceType] = frozenset(
    {SourceType.PROCESS, SourceType.DISQUALIFICATION, SourceType.BENEFIT}
)


def use_for_matching(source_type: SourceType) -> bool:
    """source_type 별 use_for_matching 값을 반환한다."""
    # 한 곳에서 규칙을 계산해 DB 저장값과 검색 조건의 불일치를 막는다.
    return source_type not in NON_MATCHING_SOURCE_TYPES


@dataclass(frozen=True)
class Chunk:
    """메모리 상의 청크. DB 저장 직전까지 임베딩이 없다."""

    source_type: SourceType
    chunk_index: int
    chunk_content: str
    content_hash: str

    @property
    def use_for_matching_flag(self) -> bool:
        # 별도 boolean 입력 없이 source_type으로 매칭 여부를 결정한다.
        return use_for_matching(self.source_type)


@dataclass(frozen=True)
class ExtractedItem:
    """LLM 구조화 추출 결과의 단위 항목(기술 스택/복리후생)."""

    name: str
    evidence: str
