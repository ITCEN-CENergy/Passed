"""텍스트 정규화와 표기 통합.

계획서 8절(공통 정규화)과 5절(지역·경력·고용형태·학력 정규화)을 따른다.
"""

from __future__ import annotations

import re

# --- 공통 정규화 ---

_BULLET_PATTERNS = [
    re.compile(r"^\s*•\s+"),       # • 불릿
    re.compile(r"^\s*[-·*]\s+"),   # - / · / * 불릿
    re.compile(r"^\s*\d+[.)]\s+"),  # 번호 목록: 1. / 1)
    re.compile(r"^\s*\(\d+\)\s+"), # (1) 형태
    re.compile(r"^\s*[ivxlcdm]+\.\s+", re.IGNORECASE),  # 로마숫자 i.
]


def _strip_bullet(line: str) -> str:
    for pat in _BULLET_PATTERNS:
        new, n = pat.subn("", line, count=1)
        if n:
            return new.strip()
    return line.strip()


def normalize_text(text: str | None) -> str:
    """공통 정규화: 줄바꿈 \\n 통일, 각 줄 trim, 불릿 제거, 빈 줄 축약.

    목록 기호는 제거하되 항목 내용은 바꾸지 않는다.
    """
    if not text:
        return ""
    # CRLF / CR -> LF
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    lines = [_strip_bullet(line) for line in text.split("\n")]
    # 연속 빈 줄을 하나로
    out: list[str] = []
    prev_blank = False
    for line in lines:
        blank = not line
        if blank and prev_blank:
            continue
        out.append(line)
        prev_blank = blank
    # 앞뒤 빈 줄 제거
    while out and not out[0]:
        out.pop(0)
    while out and not out[-1]:
        out.pop()
    return "\n".join(out)


def is_blank(text: str | None) -> bool:
    return not text or not text.strip()


# --- 표기 정규화(별칭 -> 표준값) ---

REGION_ALIASES: dict[str, str] = {
    "서울": "서울특별시",
    "서울시": "서울특별시",
    "부산": "부산광역시",
    "부산시": "부산광역시",
    "대구": "대구광역시",
    "대구시": "대구광역시",
    "인천": "인천광역시",
    "인천시": "인천광역시",
    "광주": "광주광역시",
    "광주시": "광주광역시",
    "대전": "대전광역시",
    "대전시": "대전광역시",
    "울산": "울산광역시",
    "울산시": "울산광역시",
    "세종": "세종특별자치시",
    "세종시": "세종특별자치시",
    "경기": "경기도",
    "경기도": "경기도",
    "강원": "강원특별자치도",
    "강원도": "강원특별자치도",
    "충북": "충청북도",
    "충청북도": "충청북도",
    "충남": "충청남도",
    "충청남도": "충청남도",
    "전북": "전북특별자치도",
    "전라북도": "전북특별자치도",
    "전남": "전라남도",
    "전라남도": "전라남도",
    "경북": "경상북도",
    "경상북도": "경상북도",
    "경남": "경상남도",
    "경상남도": "경상남도",
    "제주": "제주특별자치도",
    "제주도": "제주특별자치도",
}

CAREER_ALIASES: dict[str, str] = {
    "신입": "신입",
    "경력": "경력",
    "신입/경력": "신입/경력",
    "경력무관": "무관",
    "무관": "무관",
}

HIRE_TYPE_ALIASES: dict[str, str] = {
    "정규직": "정규직",
    "계약직": "계약직",
    "무기계약직": "무기계약직",
    "파견직": "파견직",
    "인턴직": "인턴직",
    "일용직": "일용직",
    "위촉직": "위촉직",
    "프리랜서": "프리랜서",
}

EDU_LEVEL_ALIASES: dict[str, str] = {
    "학력무관": "학력무관",
    "고졸": "고등학교졸업",
    "고등학교졸업": "고등학교졸업",
    "초대졸": "전문학사",
    "전문학사": "전문학사",
    "전문대졸": "전문학사",
    "대졸": "학사",
    "학사": "학사",
    "학사 이상": "학사 이상",
    "전문학사 이상": "전문학사 이상",
    "석사": "석사",
    "석사 이상": "석사 이상",
    "박사": "박사",
    "박사 이상": "박사 이상",
}


def _resolve(alias_map: dict[str, str], value: str | None) -> str | None:
    """별칭 사전으로 표준값 변환. 없으면 정규화된 원문을 그대로 둔다."""
    if value is None:
        return None
    key = value.strip()
    if not key:
        return None
    if key in alias_map:
        return alias_map[key]
    lower_map = {k.lower(): v for k, v in alias_map.items()}
    return lower_map.get(key.lower(), key)


def normalize_region(value: str | None) -> str | None:
    return _resolve(REGION_ALIASES, value)


def normalize_career(value: str | None) -> str | None:
    return _resolve(CAREER_ALIASES, value)


def normalize_hire_type(value: str | None) -> str | None:
    return _resolve(HIRE_TYPE_ALIASES, value)


def normalize_edu_level(value: str | None) -> str | None:
    return _resolve(EDU_LEVEL_ALIASES, value)


# --- 기술 스택 별칭 정규화(계획서 10절) ---

TECH_STACK_ALIASES: dict[str, str] = {
    "kotlin": "Kotlin",
    "java": "Java",
    "spring": "Spring",
    "spring boot": "Spring Boot",
    "springboot": "Spring Boot",
    "node": "Node.js",
    "nodejs": "Node.js",
    "node.js": "Node.js",
    "react": "React",
    "vue": "Vue.js",
    "python": "Python",
    "django": "Django",
    "fastapi": "FastAPI",
    "go": "Go",
    "golang": "Go",
    "rust": "Rust",
    "c++": "C++",
    "c#": "C#",
    ".net": ".NET",
    "postgres": "PostgreSQL",
    "postgresql": "PostgreSQL",
    "mysql": "MySQL",
    "mariadb": "MariaDB",
    "mongodb": "MongoDB",
    "redis": "Redis",
    "kafka": "Kafka",
    "rabbitmq": "RabbitMQ",
    "docker": "Docker",
    "kubernetes": "Kubernetes",
    "k8s": "Kubernetes",
    "aws": "AWS",
    "gcp": "GCP",
    "azure": "Azure",
    "terraform": "Terraform",
    "jenkins": "Jenkins",
    "git": "Git",
    "typescript": "TypeScript",
    "javascript": "JavaScript",
    "jquery": "jQuery",
    "html": "HTML",
    "css": "CSS",
    "sql": "SQL",
    "linux": "Linux",
}


def normalize_tech_name(name: str) -> str | None:
    """기술 스택 별칭·대소문자 통합. 빈 값/사전 미포함면 정규화된 원문."""
    key = (name or "").strip()
    if not key:
        return None
    return TECH_STACK_ALIASES.get(key.lower(), key)
