"""텍스트 정규화·청킹·content_hash 계산(계획서 6·8·9절).

목록형 텍스트는 항목 하나당 한 청크, 채용 절차는 원문 그대로,
서술형 텍스트는 문단·토큰 기반 분할에 오버랩을 적용한다.
빈 요소는 DB의 빈 문자열 금지 제약에 맞춰 청크를 생성하지 않는다.
"""

from __future__ import annotations

import hashlib
import re

import tiktoken

from .models import Chunk, SourceType
from .normalize import normalize_text

_enc: tiktoken.Encoding | None = None


# ---------------------------------------------------------------------------
# 토큰 계산과 해시
# ---------------------------------------------------------------------------
def get_encoding() -> tiktoken.Encoding:
    """text-embedding-3-small 기준 토크나이저(cl100k_base)를 캐싱한다."""
    global _enc
    if _enc is None:
        _enc = tiktoken.get_encoding("cl100k_base")
    return _enc


def token_count(text: str) -> int:
    if not text:
        return 0
    return len(get_encoding().encode(text))


def content_hash(text: str) -> str:
    """정규화된 최종 문자열의 SHA-256 해시."""
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


EMPTY_CONTENT_HASH = content_hash("")


# ---------------------------------------------------------------------------
# 원문 유형별 분할
# ---------------------------------------------------------------------------
def split_list_items(text: str) -> list[str]:
    """목록형 텍스트를 줄 단위 항목으로 분리.

    normalize_text 단계에서 불릿·번호 기호는 이미 제거되므로,
    여기서는 비어있지 않은 각 줄을 항목 하나로 취급한다.
    """
    return [line.strip() for line in text.split("\n") if line.strip()]


def _token_split(text: str, max_tokens: int, overlap: int) -> list[str]:
    """토큰 기준 분할에 오버랩 적용."""
    enc = get_encoding()
    tokens = enc.encode(text)
    if len(tokens) <= max_tokens:
        return [text]
    # 다음 시작점을 overlap만큼 앞당겨 청크 경계의 문맥 손실을 줄인다.
    step = max(1, max_tokens - overlap)
    pieces: list[str] = []
    start = 0
    while start < len(tokens):
        end = min(start + max_tokens, len(tokens))
        pieces.append(enc.decode(tokens[start:end]))
        if end >= len(tokens):
            break
        start += step
    return pieces


def split_narrative(text: str, max_tokens: int, overlap: int) -> list[str]:
    """서술형 텍스트 분할.

    문단 경계(빈 줄)를 우선 보존하고, 문단을 최대 토큰 수 안에서 묶는다.
    최대 토큰 수를 넘으면 토큰 기준 추가 분할, 인접 청크에 오버랩 적용.
    """
    paragraphs = [p.strip() for p in re.split(r"\n\s*\n", text) if p.strip()]
    if not paragraphs:
        return []
    chunks: list[str] = []
    buf = ""
    # 짧은 문단은 가능한 한 하나의 청크로 묶고 긴 문단만 토큰 분할한다.
    for para in paragraphs:
        candidate = f"{buf}\n{para}" if buf else para
        if token_count(candidate) <= max_tokens:
            buf = candidate
            continue
        # buf 가 찼으면 flush
        if buf:
            chunks.append(buf)
            buf = ""
        if token_count(para) <= max_tokens:
            buf = para
        else:
            chunks.extend(_token_split(para, max_tokens, overlap))
    if buf:
        chunks.append(buf)
    return chunks


def _emit(
    source_type: SourceType,
    raw_items: list[str],
    max_tokens: int,
    overlap: int,
    split_long_items: bool,
) -> list[Chunk]:
    """항목 리스트를 Chunk 리스트로 변환.

    비어있으면 빈 목록을 반환한다. split_long_items=True 면
    단일 항목이 최대 토큰 수를 넘을 때 토큰 분할한다(청크 인덱스는 유지).
    """
    if not raw_items or all(not item for item in raw_items):
        return []
    # 모든 source_type에서 chunk_index를 0부터 연속적으로 부여한다.
    chunks: list[Chunk] = []
    idx = 0
    for item in raw_items:
        if not item:
            continue
        if split_long_items and token_count(item) > max_tokens:
            sub_pieces = _token_split(item, max_tokens, overlap)
        else:
            sub_pieces = [item]
        for sp in sub_pieces:
            chunks.append(Chunk(source_type, idx, sp, content_hash(sp)))
            idx += 1
    if not chunks:
        return []
    return chunks


# ---------------------------------------------------------------------------
# 공고 원문을 DB 청크로 변환
# ---------------------------------------------------------------------------
def build_chunks(
    posting: dict,
    max_tokens: int,
    overlap: int,
) -> list[Chunk]:
    """CSV에서 저장한 공고 원문만으로 전체 청크 리스트를 생성.

    posting 은 fetch_posting 결과(원문 컬럼 포함).
    """
    chunks: list[Chunk] = []

    # 공통 정규화 후 값이 있는 필드만 청크로 만든다.
    def add(source_type: SourceType, raw: str | None, splitter) -> None:
        normalized = normalize_text(raw)
        if not normalized:
            return
        items = splitter(normalized)
        chunks.extend(_emit(source_type, items, max_tokens, overlap, True))

    # 기본 텍스트 요소 분리
    add(
        SourceType.POSITION_DETAIL,
        posting.get("position_detail"),
        lambda text: split_narrative(text, max_tokens, overlap),
    )
    add(SourceType.MAIN_TASK, posting.get("main_duty"), split_list_items)
    add(SourceType.REQUIREMENT, posting.get("qualification"), split_list_items)
    add(SourceType.PREFERENCE, posting.get("preference"), split_list_items)
    add(SourceType.DISQUALIFICATION, posting.get("disqualify_reason"), split_list_items)

    # 채용 절차: 분리하지 않고 원문 그대로 한 행
    process = normalize_text(posting.get("process"))
    if process:
        chunks.append(Chunk(SourceType.PROCESS, 0, process, content_hash(process)))

    return chunks
