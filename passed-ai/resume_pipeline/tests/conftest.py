from __future__ import annotations

import sys
from pathlib import Path

PASSED_AI_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(PASSED_AI_ROOT))
