"""텍스트 정규화·청킹·content_hash 계산(계획서 6·8·9절).

목록형 텍스트는 항목 하나당 한 청크, 채용 절차는 원문 그대로,
서술형 텍스트는 문단·토큰 기반 분할에 오버랩을 적용한다.
빈 요소도 해당 source_type 의 빈 청크 한 행을 만든다.
"""

from __future__ import annotations

import hashlib
import re

import tiktoken

from .models import Chunk, ExtractedItem, SourceType
from .normalize import normalize_tech_name, normalize_text

_enc: tiktoken.Encoding | None = None


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

    비어있으면 빈 청크 한 행을 만든다. split_long_items=True 면
    단일 항목이 최대 토큰 수를 넘을 때 토큰 분할한다(청크 인덱스는 유지).
    """
    if not raw_items or all(not item for item in raw_items):
        return [Chunk(source_type, 0, "", EMPTY_CONTENT_HASH)]
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
        return [Chunk(source_type, 0, "", EMPTY_CONTENT_HASH)]
    return chunks


def _build_tech_chunks(tech_stacks: list[ExtractedItem]) -> list[Chunk]:
    """기술 스택: 기술 하나당 한 행. 정규화·중복 제거."""
    seen: set[str] = set()
    names: list[str] = []
    for item in tech_stacks:
        std = normalize_tech_name(item.name)
        if std is None or std in seen:
            continue
        seen.add(std)
        names.append(std)
    return _emit(SourceType.TECH_STACK, names, 0, 0, split_long_items=False)


def _build_benefit_chunks(benefits: list[ExtractedItem]) -> list[Chunk]:
    """복리후생: 항목 하나당 한 행. 중복 제거."""
    seen: set[str] = set()
    items: list[str] = []
    for item in benefits:
        v = item.name.strip()
        if not v or v in seen:
            continue
        seen.add(v)
        items.append(v)
    return _emit(SourceType.BENEFIT, items, 0, 0, split_long_items=False)


def build_chunks(
    posting: dict,
    tech_stacks: list[ExtractedItem] | None,
    benefits: list[ExtractedItem] | None,
    max_tokens: int,
    overlap: int,
) -> list[Chunk]:
    """공고 원문과 LLM 추출 결과로 전체 청크 리스트를 생성.

    posting 은 fetch_posting 결과(원문 컬럼 포함).
    """
    tech_stacks = tech_stacks or []
    benefits = benefits or []
    chunks: list[Chunk] = []

    def add(source_type: SourceType, raw: str | None, splitter) -> None:
        normalized = normalize_text(raw)
        if not normalized:
            chunks.append(Chunk(source_type, 0, "", EMPTY_CONTENT_HASH))
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
    else:
        chunks.append(Chunk(SourceType.PROCESS, 0, "", EMPTY_CONTENT_HASH))

    # LLM 추출 결과
    chunks.extend(_build_tech_chunks(tech_stacks))
    chunks.extend(_build_benefit_chunks(benefits))

    return chunks
