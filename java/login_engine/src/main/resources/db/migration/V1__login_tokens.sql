-- Google-token storage per user. Access + refresh tokens are stored encrypted at rest
-- via TokenCipher (AES-GCM). See spec §9.1 and Open Q #5.
--
-- Keyed by Google `sub` (subject) rather than a surrogate ID — decided 2026-07-25 (Open Q #16).
-- This is the same ID used as the `sub` claim on our session JWTs and (later) as the PK on
-- khata_users in user_engine.

CREATE TABLE IF NOT EXISTS login_tokens (
    user_id             TEXT        PRIMARY KEY,
    access_token        TEXT,
    access_token_expiry TIMESTAMPTZ,
    refresh_token       TEXT,
    scope               TEXT        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Maintain updated_at on any UPDATE. Cheap trigger — no application-side rules to remember.
CREATE OR REPLACE FUNCTION login_tokens_touch_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_login_tokens_touch ON login_tokens;
CREATE TRIGGER trg_login_tokens_touch
    BEFORE UPDATE ON login_tokens
    FOR EACH ROW
    EXECUTE FUNCTION login_tokens_touch_updated_at();
