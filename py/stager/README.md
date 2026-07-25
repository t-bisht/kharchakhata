# stager

Lands raw artefacts fetched by `ingestion`, normalises, hands off downstream.

```bash
cd py
uv run --package stager uvicorn stager.api:app --reload --port 8081
```
