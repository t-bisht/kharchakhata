# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**KharchaKhata** (also spelled "KarchaKhata" in logs) — personal expense tracker. Scans bills/receipts from Gmail (and later other sources) to extract and track expenses.

Planned stack: **Java** backend + **React** frontend web app. No source code exists yet — repository currently holds only setup notes and Google OAuth credentials for Gmail API access.

## Current State

Repo is at bootstrap stage. Only contents:
- `resources/gcloud_app_credentials.json` — Google OAuth desktop-app client credentials (gitignored)
- `resources/g_credentials.md` — plaintext record of client_id/secret and GCP project (`tbpersonal`) (gitignored)
- `resources/jlog/app_development_log.md` — running dev journal ("Day N" format). Append notes here as work progresses.

No build system, no `package.json`, no `pom.xml`/`build.gradle`, no tests yet. Build/lint/test commands will be added when scaffolding lands.

## Gmail OAuth Context

Auth flow uses Google OAuth 2.0 with **desktop application** client type under GCP project `tbpersonal`:
1. `gcloud_app_credentials.json` identifies the app to Google.
2. Google opens user consent window, returns access token (short-lived, sent with every Gmail API call) + refresh token (used to mint new access tokens; long-lived except in special revocation cases).
3. Scopes granted per user session.

Reference: https://developers.google.com/identity/protocols/oauth2

Multiple clients can exist under one branding — role of that distinction still TBD per dev log.

## Conventions

- Credentials files (`g_credentials.md`, `gcloud_app_credentials.json`) are gitignored — never commit them or their contents.
- Log substantive setup/design decisions in `resources/jlog/app_development_log.md` under the appropriate Day heading.
