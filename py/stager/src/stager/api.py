from fastapi import FastAPI

app = FastAPI(title="kharchakhata-stager")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
