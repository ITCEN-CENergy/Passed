"""이력서·자기소개서 청킹과 스킬 추출을 위한 배치 파이프라인."""

from pathlib import Path

from dotenv import load_dotenv

# 다른 모듈을 import하기 전에 환경변수를 먼저 로드해야 한다.
load_dotenv(Path(__file__).resolve().parent.parent / ".env")

from .pipeline import run_chunking_for_user  # noqa: E402

__all__ = ["run_chunking_for_user"]