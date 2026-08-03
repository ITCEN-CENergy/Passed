from fastapi import FastAPI


app = FastAPI(
    title="Passed AI API",
    version="0.1.0",
)


@app.get("/")
async def root() -> dict[str, str]:
    return {"message": "Passed AI server is running"}


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000)
