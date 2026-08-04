"""결정적인 텍스트 정규화와 해시 함수."""

from __future__ import annotations

from datetime import date, datetime
import hashlib
import re
import unicodedata
from typing import Any


def normalize_text(value: Any) -> str:
    if value is None:
        return ""
    text = unicodedata.normalize("NFKC", str(value))
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    lines = [re.sub(r"[ \t]+", " ", line).strip() for line in text.split("\n")]
    normalized: list[str] = []
    previous_blank = False
    for line in lines:
        if not line:
            if normalized and not previous_blank:
                normalized.append("")
            previous_blank = True
            continue
        normalized.append(line)
        previous_blank = False
    return "\n".join(normalized).strip()


def format_date(value: Any) -> str:
    if value is None or value == "":
        return ""
    if isinstance(value, datetime):
        return value.date().isoformat()
    if isinstance(value, date):
        return value.isoformat()
    return normalize_text(value)


def format_period(start: Any, end: Any, *, ongoing: bool = False) -> str:
    start_text = format_date(start)
    end_text = "재직 중" if ongoing else format_date(end)
    if start_text and end_text:
        return f"{start_text} ~ {end_text}"
    if start_text:
        return f"{start_text} ~"
    if end_text:
        return f"~ {end_text}"
    return ""


def content_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()
