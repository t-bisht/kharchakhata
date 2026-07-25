# Build context: repo root (kharchakhata/).

FROM python:3.12-slim

RUN pip install --no-cache-dir uv

WORKDIR /workspace

COPY py/pyproject.toml ./py/
COPY py/ingestion/pyproject.toml ./py/ingestion/
COPY py/stager/pyproject.toml ./py/stager/
COPY py/uv.lock ./py/

WORKDIR /workspace/py
RUN uv sync --package stager --no-dev --frozen

WORKDIR /workspace
COPY py/stager ./py/stager

ENV PYTHONUNBUFFERED=1
EXPOSE 8081

WORKDIR /workspace/py
ENTRYPOINT ["uv", "run", "--package", "stager", "uvicorn"]
CMD ["stager.api:app", "--host", "0.0.0.0", "--port", "8081", "--app-dir", "stager/src"]
