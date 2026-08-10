FROM python:3.14-slim

WORKDIR /app

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

COPY pyproject.toml uv.lock ./

RUN uv sync \
    --locked \
    --no-dev \
    --no-install-project

COPY . .

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

# FastAPI 진입점과 기능 모듈을 복사합니다.
COPY --chown=app:app app ./app
COPY --chown=app:app api ./api
COPY --chown=app:app core ./core
COPY --chown=app:app resume_pipeline ./resume_pipeline
RUN uv sync \
    --locked \
    --no-dev

ENV PATH="/app/.venv/bin:$PATH"

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
CMD [
    "uvicorn",
    "app.main:app",
    "--host",
    "0.0.0.0",
    "--port",
    "8000"
]
