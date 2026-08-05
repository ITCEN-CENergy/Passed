from fastapi import FastAPI

from api.features.roadmap.router import router as roadmap_router
from api.features.coverletter.router import router as coverletter_router


app = FastAPI(
    title="Passed AI API",
    version="0.1.0",
)

app.include_router(roadmap_router)
app.include_router(coverletter_router)


@app.get("/")
async def root() -> dict[str, str]:
    return {"message": "Passed AI server is running"}


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000)
