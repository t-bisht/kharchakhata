---
name: spec-construction
description: Build a concise, table-driven technical spec for a change in this monorepo. Uses PlantUML for architecture + sequence diagrams, covers API contracts, DB schema, exceptions, and deployment strategy inferred from the repo. Trigger when the user says "write a spec", "spec this out", "create a design doc", "/spec", or invokes /spec-construction.
---

# spec-construction

Generates one self-contained `.md` spec per change. Terse, table-first, engine-specific. Uses PlantUML fenced blocks for diagrams — never ASCII art.

## When invoked

The user names a change ("spec the Gmail ingestion pipe", "spec for user login"). If they don't, ask one question: **"what feature / change?"** Then proceed.

## Workflow

1. **Locate the affected engine.** Grep `/py`, `/java`, `/web`, `/infra` to identify which subprojects the change touches. Read the relevant `pyproject.toml` / `build.gradle` / `package.json` to learn the runtime stack. Do not spec against a generic template — every table below must reference real files, ports, and dependencies.
2. **Gather constraints via ≤3 targeted questions**, only for facts you cannot derive from code:
   - Ambiguous scope (which endpoints? which entities?)
   - External systems not visible in the repo (third-party APIs, quotas)
   - Non-functional targets (latency, throughput, retention)
   Skip the ask if the code answers it.
3. **Write the spec** to `resources/specs/YYYY-MM-DD-<kebab-slug>.md` using today's date. Copy `.claude/skills/spec-construction/TEMPLATE.md` as the starting point and fill every section. Drop sections that don't apply (say so in a one-liner: `_N/A — read-only change_`).
4. **Report** to the user with the file path and a 2-line summary. Nothing else.

## Hard rules

- **Tables over prose.** Every section that can be a table, is. Free text only where a table would be silly (motivation, one-line rationales).
- **PlantUML only for diagrams.** Fenced ` ```plantuml ` block with `@startuml` / `@enduml`. Two diagram types allowed:
  - **Component / class** — show what changes structurally. New nodes marked `<<new>>`, altered `<<changed>>`, removed `<<removed>>`.
  - **Sequence** — one per distinct workflow (happy path, main failure path). Use `participant` for each service by its compose name (`web`, `user_engine`, `ingestion`, `stager`, `postgres`).
- **Engine-specific.** API contract tables use the framework's real conventions: FastAPI → pydantic model names; Spring → DTO class + validation annotations; React → hook/route names.
- **Contracts, not narratives.** For every endpoint: method, path, request schema, response schema, auth, status codes. For every table: column, type, constraints, index, migration file.
- **Exception table is mandatory** for any change that adds error paths. Columns: trigger condition, exception/error type, HTTP status (or side-effect), user-visible message, log level.
- **Deployment section is inferred**, not asked. Pull from `infra/docker-compose.yml` — which service, which image, which env vars change, whether `POSTGRES_*` init or Spring `bootJar` rebuild is required. If the change adds a new service, add a `docker-compose.yml` diff table.
- **Length cap.** Body ≤ ~400 lines. If longer, split into `<slug>-part-2.md` and cross-link. No filler, no restating requirements the tables already carry.
- **No secrets.** Never paste values from `resources/g_credentials.md` or `gcloud_app_credentials.json`. Reference by name only.

## Section order (from TEMPLATE.md)

1. Header table (feature, engine(s), owner, status, date, related jlog day)
2. Motivation — ≤3 sentences
3. Scope table (In / Out)
4. Architecture change — PlantUML component diagram
5. Workflows — one PlantUML sequence per flow
6. API contracts — one sub-table per endpoint
7. Database schema — table-per-table, plus migration list
8. Exception handling table
9. Deployment strategy table + compose diff (if any)
10. Test plan table
11. Open questions table

## Anti-patterns to refuse

- Verbose "background" paragraphs that duplicate CLAUDE.md.
- Sequence diagrams drawn in ASCII.
- API sections written as bullet lists instead of tables.
- "TBD" everywhere — if genuinely unknown, put it in the Open Questions table with an owner and a decision-by date.
- Speculative future phases. Spec only the change being proposed now.

## After writing

Do **not** commit. Do **not** open a PR. Just print the path.
