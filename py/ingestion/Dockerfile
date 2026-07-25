# Build context: repo root (kharchakhata/). Set in docker-compose.yml so the
# uv workspace pyproject.toml (py/pyproject.toml) and the member pyproject files
# are all reachable in the copy phase.

FROM python:3.12-slim

RUN pip install --no-cache-dir uv

WORKDIR /workspace

# Copy workspace root + all member manifests so `uv sync` can resolve the graph.
COPY py/pyproject.toml ./py/
COPY py/ingestion/pyproject.toml ./py/ingestion/
COPY py/stager/pyproject.toml ./py/stager/
# Lockfile committed for reproducible installs.
COPY py/uv.lock ./py/

WORKDIR /workspace/py
RUN uv sync --package ingestion --no-dev --frozen

WORKDIR /workspace
COPY py/ingestion ./py/ingestion

ENV PYTHONUNBUFFERED=1
EXPOSE 8080

WORKDIR /workspace/py
ENTRYPOINT ["uv", "run", "--package", "ingestion", "uvicorn"]
CMD ["ingestion.api:app", "--host", "0.0.0.0", "--port", "8080", "--app-dir", "ingestion/src"]
