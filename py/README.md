# py — KharchaKhata python workspace

uv-managed workspace. Each member is its own container-shippable service.

## Members

- `ingestion` — fetches bills/receipts from Gmail (and later other sources).
- `stager` — lands raw fetched artefacts, normalises, hands off downstream.

## Usage

```bash
cd py
uv sync                                # install all workspace deps
uv run --package ingestion pytest      # run one member's tests
uv run --package ingestion uvicorn ingestion.api:app --reload
```
