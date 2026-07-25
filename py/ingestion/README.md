# ingestion

Fetches bills/receipts from Gmail (and later other sources).

Entrypoint: `ingestion.api:app` (FastAPI).

```bash
cd py
uv run --package ingestion uvicorn ingestion.api:app --reload --port 8080
```
