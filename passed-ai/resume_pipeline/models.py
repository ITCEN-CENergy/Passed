"""resume_pipeline에서 공유하는 도메인 모델."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class ResumeSourceType(str, Enum):
    EDUCATION = "EDUCATION"
    EXPERIENCE = "EXPERIENCE"
    ACTIVITY = "ACTIVITY"
    TRAINING = "TRAINING"
    CERTIFICATION = "CERTIFICATION"
    AWARD = "AWARD"
    OVERSEAS_EXPERIENCE = "OVERSEAS_EXPERIENCE"
    LANGUAGE = "LANGUAGE"


@dataclass(frozen=True)
class ResumeChunk:
    resume_id: int
    source_type: ResumeSourceType
    source_id: int
    chunk_index: int
    chunk_content: str
    content_hash: str

    @property
    def key(self) -> tuple[str, int, int]:
        return self.source_type.value, self.source_id, self.chunk_index


@dataclass(frozen=True)
class CoverLetterChunk:
    cover_letter_item_id: int
    chunk_index: int
    chunk_content: str
    content_hash: str

    @property
    def key(self) -> int:
        return self.chunk_index


@dataclass(frozen=True)
class SyncStats:
    inserted: int = 0
    updated: int = 0
    deleted: int = 0
    unchanged: int = 0


@dataclass(frozen=True)
class EmbeddingStats:
    """청크 임베딩 작업 한 번의 누적 처리 결과."""

    embedded: int = 0
    failed: int = 0
    skipped: int = 0


@dataclass(frozen=True)
class ChunkingResult:
    resume_chunks: SyncStats
    cover_letter_chunks: SyncStats
