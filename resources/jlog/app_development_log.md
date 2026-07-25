# KarchaKhata Journey Log

## Day 1

- Created client ID using which OAUTH would happen with GMAIL
    - created branding -> audience -> clients (All based on desktop application)
    - mutiple clients can be made under the same branding but I am not sure what to make of it now
    - saved the credentials under gcloud_app_credentials.json
        - This credentials.json identifies the application
        - This validates the applicaiton and google will open the user authentication window
        - Once authenticated google will send tokens (application + user + scope)
            - Google will send two types of tokens
                - Access tokens : These tokens are sent with every GMAIL API request & expire
                - Refresh tokens : These tokens are used to generate new access tokens once they are expired
                    - They expire in special cases
      - Would be good to know how OAUTH is working here - what is branding / audience and
        client : https://developers.google.com/identity/protocols/oauth2

- initializing claude in the repository

## Day 2

- Bootstrapped the monorepo layout. Top-level dirs: `/py`, `/java`, `/web`, `/infra`.
    - `/py` — uv workspace, members `ingestion` + `stager`. Each is a hatchling
      package with `src/<pkg>/` layout, own `pyproject.toml`, own Dockerfile,
      FastAPI `/health` stub, and a pytest smoke test.
    - `/java` — Gradle multi-project. Group id `org.tb.khata`, Java 21.
      Shared build logic in `buildSrc/` as `khata.java-conventions` (Spotless
      + JaCoCo + JUnit 5) and `khata.spring-conventions` (Spring BOM + Lombok
      + Testcontainers BOM). Versions in `gradle/libs.versions.toml`. Copied
      the Gradle 8.8 wrapper from `art-microservices`. Only subproject so far:
      `user_engine` (Spring Boot, port 8082) with a `UserEngineApplication`
      entrypoint and `application.yml` wired for postgres + Google OAuth env
      vars. Multi-stage Dockerfile uses Spring Boot layertools.
    - `/web` — Vite + React 18 + TS + Tailwind. Path alias `@/` → `src/`.
      Minimal `App.tsx`, `globals.css`, `main.tsx`. nginx runtime image with
      SPA fallback + `/api`, `/ingestion`, `/stager` proxies. Runtime env
      injected via `docker-entrypoint.sh` → `/env.js` (no rebuild per env).
    - `/infra` — `docker-compose.yml` for the full stack (postgres, ingestion,
      stager, user_engine, web) on a shared `kk-net` bridge. Postgres init
      SQL drop-dir at `infra/postgres/init/`. `.env.example` documents vars.
- Root `.gitignore` merged patterns for Python/uv, Gradle, Node, IDE files,
  plus the existing OAuth-credential exclusions.
- Convention decisions locked in:
    - Python packages get **no prefix** (`ingestion`, `stager`) — kept short.
    - Java group id is **`org.tb.khata`** (personal namespace).
    - `uv.lock` will be committed once the workspace is first synced —
      mirrors the arthasetu convention for reproducible container builds.
- Not yet done: `uv sync` to generate the lockfile, initial `./gradlew build`,
  and `npm install` — will run when first working on each stack.

