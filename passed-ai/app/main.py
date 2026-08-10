from contextlib import asynccontextmanager
import logging
import os
from time import perf_counter

import httpx
from fastapi import FastAPI, Request
from prometheus_client import Counter, Histogram, make_asgi_app

from api.features.roadmap.router import router as roadmap_router
from api.features.coverletter.router import router as coverletter_router
from api.features.recommendation.router import router as recommendation_router
from api.features.user_skill.router import router as user_skill_router
from api.features.skill_gap.router import router as skill_gap_router


log_level_name = os.getenv("LOG_LEVEL", "INFO").upper()
log_level = getattr(logging, log_level_name, logging.INFO)
logging.basicConfig(
    level=log_level,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logging.getLogger("api.features.roadmap").setLevel(log_level)
for library_logger_name in ("httpx", "httpcore", "openai"):
    logging.getLogger(library_logger_name).setLevel(logging.WARNING)


AI_REQUEST_TOTAL = Counter(
    "ai_inference_requests_total",
    "Total AI service requests",
)

AI_ERROR_TOTAL = Counter(
    "ai_inference_errors_total",
    "Total AI service errors",
)

AI_INFERENCE_SECONDS = Histogram(
    "ai_inference_duration_seconds",
    "AI service request duration",
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    async with httpx.AsyncClient() as http_client:
        app.state.http_client = http_client
        yield


app = FastAPI(
    title="Passed AI API",
    version="0.1.0",
    lifespan=lifespan,
)

# Prometheus 설정
metrics_app = make_asgi_app()

app.mount("/metrics", metrics_app)

app.include_router(roadmap_router)
app.include_router(coverletter_router)
app.include_router(recommendation_router)
app.include_router(user_skill_router)
app.include_router(skill_gap_router)


@app.get("/")
async def root() -> dict[str, str]:
    return {"message": "Passed AI server is running"}


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.middleware("http")
async def record_request_metrics(request: Request, call_next):
    if request.url.path in {"/health", "/metrics"}:
        return await call_next(request)

    AI_REQUEST_TOTAL.inc()
    started_at = perf_counter()
    try:
        response = await call_next(request)
        if response.status_code >= 500:
            AI_ERROR_TOTAL.inc()
        return response
    except Exception:
        AI_ERROR_TOTAL.inc()
        raise
    finally:
        AI_INFERENCE_SECONDS.observe(perf_counter() - started_at)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=8000)
