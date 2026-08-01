# auth_engine — class map

Companion to `resources/specs/2026-07-25-auth-engine.md`. Quick reference: what each class does, who calls it, which spec section it implements. Package root: `org.tb.khata.login.auth`.

---

## Web layer (`auth/web/`)

| Class | Role | Called by | Spec |
|---|---|---|---|
| `GoogleAuthController` | Browser endpoints: `/start`, `/callback`, `/logout` | Browser (302 flow) | §4.1, §4.2, §4.4 |
| `JwksController` | Serves RSA public key as JWK Set at `/.well-known/jwks.json` | Sub-engines at startup + on `kid` cache miss | §4.5 |
| `InternalTokenController` | `POST /internal/tokens/access` + `.../invalidate` | Ingestion, future engines | §4.6, §4.7, §4.8, §4.9 |
| `AuthExceptionHandler` | `@ControllerAdvice` — maps `AuthFlowException` → `302 /login?err=<code>` | Spring MVC on any exception from `web.*` | §10 (browser side) |
| `InternalExceptionHandler` | `@ControllerAdvice` — maps internal exceptions → JSON `{code}` body | Spring MVC on internal-side exceptions | §10 (internal side) |

## Domain / service layer (`auth/`)

| Class | Role | Called by | Spec |
|---|---|---|---|
| `OAuthStateGenerator` | 32-byte CSPRNG → base64url (43 chars). CSRF token generator. | `GoogleAuthController` (state cookie + kk_csrf) | §4.1 |
| `SessionJwtIssuer` | Mints RS256 session JWT with thick claims (24 h) | `GoogleAuthController.handleGoogleCallback` | §7.1, §7.2 |
| `SessionJwtVerifier` | Verifies session JWT locally with our public key; returns claims | `InternalAuthFilter` for user context extraction | §4.5 |
| `RsaKeyProvider` | Loads PEM at boot; derives public key; fails fast on missing/malformed | `SessionJwtIssuer`, `SessionJwtVerifier`, `JwksController` | §8 |
| `LoginTokenService` | UPSERT `login_tokens` row after successful login | `GoogleAuthController` at end of `/callback` | §4.2c |
| `GoogleTokenRefresher` | Refreshes Google access_token; per-user `ReentrantLock`; wipes on `invalid_grant` | `InternalTokenController.access()` on refresh path | §4.7, §4.8 |

## Google I/O (`auth/gcp/`)

| Class | Role | Called by | Spec |
|---|---|---|---|
| `GoogleAuthUrlBuilder` | Builds Google authorization URL (client_id, scopes, state, prompt) | `GoogleAuthController.startGoogleLogin` | §4.1 |
| `GoogleOAuthClient` | POST to Google `/token` — both `exchangeCode()` and `refresh()` | `GoogleAuthController`; `GoogleTokenRefresher` | §4.2, §4.7 |
| `IdTokenClaimsReader` | Base64-decodes id_token payload; enforces `email_verified` | `GoogleAuthController.handleGoogleCallback` | §4.2, Open Q #15 |

## DTOs (`auth/dto/`)

| Class | Role |
|---|---|
| `GoogleTokenResponse` | Jackson record for Google `/token` JSON — access + refresh + id_token + expires_in |
| `IdentityClaims` | Record for extracted id_token fields — sub, email, name, picture |

## Persistence (`auth/persistence/`)

| Class | Role | Uses | Spec |
|---|---|---|---|
| `LoginToken` | JPA entity for `login_tokens` row | `@Convert(EncryptedStringConverter)` on access/refresh columns | §9.1 |
| `LoginTokenRepository` | `JpaRepository<LoginToken, String>` — save/findById (upsert by PK) | `LoginTokenService`, `GoogleTokenRefresher`, `InternalTokenController` | §9.1 |
| `EncryptedStringConverter` | JPA `@AttributeConverter` — encrypt on write / decrypt on read | Delegates to `TokenCipher` | §9.1, Open Q #5 |

## Security primitives (`auth/security/`)

| Class | Role | Used by | Spec |
|---|---|---|---|
| `TokenCipher` | AES-256-GCM encrypt/decrypt; `enc_v1:iv:ct` wire format; fresh 96-bit IV per call | `EncryptedStringConverter` | §9.1, Open Q #5 |
| `InternalAuthFilter` | X-Internal-Auth constant-time compare + Bearer JWT verify; attaches sub as request attr | Runs before Spring `AuthorizationFilter` on `/internal/**` | §6, §4.6 |

## Config (`auth/config/`)

| Class | Role | Bind prefix / bean |
|---|---|---|
| `GoogleOAuthProperties` | Google client_id, client_secret, auth_uri, token_uri, redirect_uri, scopes | `app.google.*` |
| `JwtProperties` | Private-key path, active kid, issuer, expiration hours | `app.jwt.*` |
| `TokenCipherProperties` | Base64 AES-256 key | `app.token-enc.*` |
| `InternalAuthProperties` | Shared `X-Internal-Auth` secret | `app.internal.*` |
| `RedirectAllowlistProperties` | Allowed SPA post-login paths + default | `app.oauth.post-login.*` |
| `SecurityConfig` | `SecurityFilterChain` — permits public endpoints; wires `InternalAuthFilter` | `@Configuration` |
| `TimeConfig` | `@Bean Clock` (systemUTC) — injectable everywhere for testability | `@Configuration` |

## Exceptions (`auth/exception/`)

Two families based on which advice picks them up.

### Browser-facing — extend `AuthFlowException`, mapped by `AuthExceptionHandler` to `302 /login?err=<code>`

| Class | Trigger | `err=` code | Log level |
|---|---|---|---|
| `LoginCancelledException` | Google returns `error=access_denied` | `access_denied` | INFO |
| `CsrfMismatchException` | `?state` != cookie, or CSRF cookie/header mismatch at logout | `state_invalid` | WARN |
| `GoogleTokenExchangeFailedException` | Google `/token` non-2xx or unreachable on code exchange | `code_exchange_failed` | ERROR |
| `IdTokenMalformedException` | id_token not a 3-part JWT / payload not valid JSON | `code_exchange_failed` | ERROR |
| `EmailUnverifiedException` | id_token `email_verified` is false/missing | `email_unverified` | WARN |
| `TokenPersistenceException` | `login_tokens` UPSERT throws | `db_error` | ERROR |

### Internal-facing — plain `RuntimeException`, mapped by `InternalExceptionHandler` to JSON

| Class | Trigger | HTTP + code | Log level |
|---|---|---|---|
| `InternalAuthMissingException` | `X-Internal-Auth` header absent | 401 `INTERNAL_AUTH_MISSING` | WARN |
| `InternalAuthInvalidException` | `X-Internal-Auth` doesn't match | 403 `INTERNAL_AUTH_INVALID` | ERROR |
| `UserContextRequiredException` | Bearer JWT missing / invalid | 401 `USER_CONTEXT_REQUIRED` | WARN |
| `UserContextMismatchException` | body `user_id` != JWT `sub` | 403 `USER_CONTEXT_MISMATCH` | WARN |
| `LoginTokenNotFoundException` | No `login_tokens` row for user_id | 404 `USER_NOT_FOUND` | WARN |
| `GoogleAuthRevokedException` | Google returned `invalid_grant` on refresh | 401 `GMAIL_AUTH_REVOKED` | WARN |
| `UpstreamUnavailableException` | Google `/token` 5xx / unreachable on refresh | 502 `GOOGLE_UNAVAILABLE` | ERROR |

## Entrypoint

| Class | Role |
|---|---|
| `LoginEngineApplication` | `@SpringBootApplication` + `@ConfigurationPropertiesScan("org.tb.khata.login")`; excludes `UserDetailsServiceAutoConfiguration` |

---

## Request-flow lookup

### `GET /api/auth/google/start` — spec §4.1
`GoogleAuthController.startGoogleLogin` → `OAuthStateGenerator.generate()` → `GoogleAuthUrlBuilder.build()` → `RedirectAllowlistProperties.isAllowed()` → 302 to Google, sets `kk_oauth_state` + `kk_oauth_post_login` cookies

### `GET /api/auth/google/callback` — spec §4.2
`GoogleAuthController.handleGoogleCallback` → verify CSRF → `GoogleOAuthClient.exchangeCode()` → `IdTokenClaimsReader.readClaims()` → `LoginTokenService.upsertFromGoogle()` → `SessionJwtIssuer.issue()` → 302 to SPA path, sets `kk_session` + `kk_csrf` cookies, clears oauth cookies

### `POST /api/auth/logout` — spec §4.4
`GoogleAuthController.logout` → double-submit CSRF check (`kk_csrf` cookie vs `X-CSRF-Token` header) → 204, clears `kk_session` + `kk_csrf`

### `GET /.well-known/jwks.json` — spec §4.5
`JwksController.jwks` → `RsaKeyProvider.publicKey()` → JWK Set (kty, use, alg, kid, n, e) with `Cache-Control: max-age=3600`

### `POST /internal/tokens/access` — spec §4.6 / §4.7 / §4.8
`InternalAuthFilter` (X-Internal-Auth + Bearer JWT) → `InternalTokenController.access` → DB lookup → if `expiry > now+60s`: fast path return, else `GoogleTokenRefresher.refresh()` → `GoogleOAuthClient.refresh()` → persist + return. On `invalid_grant`: wipe tokens, throw `GoogleAuthRevokedException` → `401 GMAIL_AUTH_REVOKED`.

### `POST /internal/tokens/invalidate` — spec §4.9
`InternalAuthFilter` → `InternalTokenController.invalidate` → `LoginToken.invalidateAccessToken()` (null out) → 204

---

## Not yet built (spec calls for)

| Concern | Spec ref | Notes |
|---|---|---|
| `user_engine` upsert call in `/callback` | §4.2 step 4 | Currently stubbed as `log.info("Would upsert ...")` + `TODO(user-engine)` |
| Full failure paths from §4.3 | §4.3 | `user_engine_unavailable` awaits `user_engine`; other branches already covered by `AuthExceptionHandler` |
| Multi-key rotation | §4.11 | `RsaKeyProvider` supports single active key; extend to keyset when needed |
| Profile-aware cookie `Secure` flag | §11 (implicit) | Currently off; wire via `application-<profile>.yml` for prod |
| Testcontainers integration test for `LoginTokenRepository` | §12 | Testcontainers-postgres is on classpath but no wired IT yet |
