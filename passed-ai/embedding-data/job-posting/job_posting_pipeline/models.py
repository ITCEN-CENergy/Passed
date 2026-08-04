"""도메인 모델: source_type, 매칭 여부, 청크 데이터 클래스."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class SourceType(str, Enum):
    """Flyway V3의 job_posting_chunks.source_type 허용값."""

    POSITION_DETAIL = "POSITION_DETAIL"
    MAIN_TASK = "MAIN_TASK"
    REQUIREMENT = "REQUIREMENT"
    PREFERENCE = "PREFERENCE"
    BENEFIT = "BENEFIT"
    PROCESS = "PROCESS"
    DISQUALIFICATION = "DISQUALIFICATION"


# 추천에 사용하면 안 되는 설명성·행정성 청크 유형이다.
# 현재 DB에는 boolean 컬럼이 없으며 SQL에서 이 source_type들을 제외한다.
NON_MATCHING_SOURCE_TYPES: frozenset[SourceType] = frozenset(
    {SourceType.PROCESS, SourceType.DISQUALIFICATION, SourceType.BENEFIT}
)


def use_for_matching(source_type: SourceType) -> bool:
    """source_type이 논리적인 추천 대상인지 반환한다."""
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
        # 하위 호환용 계산 속성이며 현재 DB에는 저장하지 않는다.
        return use_for_matching(self.source_type)
