"""어느 작업 디렉터리에서 실행해도 로컬 패키지를 찾도록 테스트 경로를 설정한다."""

from __future__ import annotations

import sys
from pathlib import Path

JOB_POSTING_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(JOB_POSTING_ROOT))
