"""CLI 작업의 콘솔·파일 로깅 설정."""

from __future__ import annotations

import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path

JOB_POSTING_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_LOG_DIR = JOB_POSTING_ROOT / "logs"


def configure_logging(
    worker_name: str,
    log_file: str | Path | None = None,
    level: int = logging.INFO,
) -> Path:
    """콘솔과 UTF-8 회전 로그 파일에 같은 실행 로그를 기록한다.

    로그 파일은 기본 10MB까지 기록하고 최대 5개의 이전 파일을 보관한다.
    CLI 진입점에서만 호출하므로 기존 root handler를 교체한다.
    """
    path = Path(log_file) if log_file else DEFAULT_LOG_DIR / f"{worker_name}.log"
    path = path.expanduser().resolve()
    path.parent.mkdir(parents=True, exist_ok=True)

    formatter = logging.Formatter(
        "%(asctime)s %(levelname)s %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )
    console_handler = logging.StreamHandler()
    console_handler.setFormatter(formatter)

    file_handler = RotatingFileHandler(
        path,
        maxBytes=10 * 1024 * 1024,
        backupCount=5,
        encoding="utf-8",
    )
    file_handler.setFormatter(formatter)

    logging.basicConfig(
        level=level,
        handlers=[console_handler, file_handler],
        force=True,
    )
    # 네트워크 라이브러리의 상세 HTTP 로그는 기본적으로 줄인다.
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("httpcore").setLevel(logging.WARNING)
    logging.getLogger("openai").setLevel(logging.WARNING)

    logging.getLogger(__name__).info("로그 파일: %s", path)
    return path
