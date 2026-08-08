# syntax=docker/dockerfile:1

# --------------------------------------------------
# 1단계: Python 의존성 설치
# --------------------------------------------------
FROM python:3.14-slim AS builder

# uv 공식 실행 파일을 가져옵니다.
COPY --from=ghcr.io/astral-sh/uv:latest \
    /uv \
    /uvx \
    /bin/

WORKDIR /app

ENV UV_COMPILE_BYTECODE=1
ENV UV_LINK_MODE=copy
ENV UV_PYTHON_DOWNLOADS=never

# 의존성 정의 파일만 먼저 복사해 캐시를 활용합니다.
COPY pyproject.toml uv.lock ./

# 프로젝트 소스 없이 의존성만 설치합니다.
RUN uv sync --frozen --no-dev --no-install-project


# --------------------------------------------------
# 2단계: FastAPI 실행
# --------------------------------------------------
FROM python:3.14-slim AS runtime

WORKDIR /app

# curl은 healthcheck에 사용합니다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd \
        --system \
        --gid app \
        --create-home \
        app

# builder에서 만든 가상환경을 복사합니다.
COPY --from=builder \
    --chown=app:app \
    /app/.venv \
    /app/.venv

# 애플리케이션 소스를 모두 복사합니다.
COPY --chown=app:app . .

ENV PATH="/app/.venv/bin:$PATH"
ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1

USER app:app

EXPOSE 8000

HEALTHCHECK \
    --interval=30s \
    --timeout=5s \
    --start-period=15s \
    --retries=5 \
    CMD curl --fail --silent \
        http://localhost:8000/health \
        || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--forwarded-allow-ips=*"]