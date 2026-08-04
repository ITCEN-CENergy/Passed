"""이력서·자기소개서 청킹과 스킬 추출을 위한 배치 파이프라인."""

from .pipeline import run_chunking_for_user

__all__ = ["run_chunking_for_user"]
