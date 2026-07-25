from fastapi import FastAPI

app = FastAPI(title="kharchakhata-ingestion")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
