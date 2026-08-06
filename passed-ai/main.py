import logging
import os

from fastapi import FastAPI

from api.features.roadmap.router import router as roadmap_router


log_level_name = os.getenv("LOG_LEVEL", "INFO").upper()
log_level = getattr(logging, log_level_name, logging.INFO)

logging.basicConfig(
    level=log_level,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logging.getLogger("api.features.roadmap").setLevel(log_level)
for library_logger_name in ("httpx", "httpcore", "openai"):
    logging.getLogger(library_logger_name).setLevel(logging.WARNING)


app = FastAPI(
    title="Passed AI API",
    version="0.1.0",
)

app.include_router(roadmap_router)


@app.get("/")
async def root() -> dict[str, str]:
    return {"message": "Passed AI server is running"}


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000)
