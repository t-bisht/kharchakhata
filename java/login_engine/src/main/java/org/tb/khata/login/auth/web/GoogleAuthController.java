package org.tb.khata.login.auth.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tb.khata.login.auth.LoginTokenService;
import org.tb.khata.login.auth.OAuthStateGenerator;
import org.tb.khata.login.auth.SessionJwtIssuer;
import org.tb.khata.login.auth.config.JwtProperties;
import org.tb.khata.login.auth.config.RedirectAllowlistProperties;
import org.tb.khata.login.auth.dto.GoogleTokenResponse;
import org.tb.khata.login.auth.dto.IdentityClaims;
import org.tb.khata.login.auth.exception.CsrfMismatchException;
import org.tb.khata.login.auth.exception.LoginCancelledException;
import org.tb.khata.login.auth.gcp.GoogleAuthUrlBuilder;
import org.tb.khata.login.auth.gcp.GoogleOAuthClient;
import org.tb.khata.login.auth.gcp.IdTokenClaimsReader;

/**
 * Browser-facing endpoints for the Google OAuth flow + logout.
 *
 * <p>Implements spec §4.1 ({@code /google/start}), §4.2 happy path ({@code /google/callback}), and
 * §4.4 ({@code /logout}). Failure branches from §4.3 are handled by {@link AuthExceptionHandler}.
 * Session-JWT refresh is not implemented — spec picks Pattern A (full re-login on expiry, §4.10).
 *
 * <p>Cookies:
 *
 * <ul>
 *   <li>{@code kk_oauth_state} — set at {@code /start}, consumed at {@code /callback} (CSRF)
 *   <li>{@code kk_oauth_post_login} — set at {@code /start}, consumed at {@code /callback} (route
 *       user back to intended SPA path)
 *   <li>{@code kk_session} — set at {@code /callback} (24 h RS256 JWT; the actual session token)
 *   <li>{@code kk_csrf} — set at {@code /callback} (double-submit CSRF token, non-HttpOnly so the
 *       SPA can echo it back on unsafe methods like logout)
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class GoogleAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);

    // Cookie names
    static final String STATE_COOKIE = "kk_oauth_state";
    static final String POST_LOGIN_COOKIE = "kk_oauth_post_login";
    static final String SESSION_COOKIE = "kk_session";
    static final String CSRF_COOKIE = "kk_csrf";
    static final String CSRF_HEADER = "X-CSRF-Token";

    // Cookie paths
    static final String OAUTH_COOKIE_PATH = "/api/auth/";
    static final String SESSION_COOKIE_PATH = "/";

    // Cookie TTLs
    static final long OAUTH_COOKIE_TTL_SECONDS = 600L; // 10 min — spec §4.1

    private final OAuthStateGenerator stateGenerator;
    private final GoogleAuthUrlBuilder authUrlBuilder;
    private final GoogleOAuthClient googleClient;
    private final IdTokenClaimsReader idTokenReader;
    private final SessionJwtIssuer jwtIssuer;
    private final LoginTokenService loginTokenService;
    private final RedirectAllowlistProperties postLogin;
    private final JwtProperties jwtProps;

    public GoogleAuthController(
            OAuthStateGenerator stateGenerator,
            GoogleAuthUrlBuilder authUrlBuilder,
            GoogleOAuthClient googleClient,
            IdTokenClaimsReader idTokenReader,
            SessionJwtIssuer jwtIssuer,
            LoginTokenService loginTokenService,
            RedirectAllowlistProperties postLogin,
            JwtProperties jwtProps) {
        this.stateGenerator = stateGenerator;
        this.authUrlBuilder = authUrlBuilder;
        this.googleClient = googleClient;
        this.idTokenReader = idTokenReader;
        this.jwtIssuer = jwtIssuer;
        this.loginTokenService = loginTokenService;
        this.postLogin = postLogin;
        this.jwtProps = jwtProps;
    }

    // ─── §4.1 — /google/start ───────────────────────────────────────────────

    /**
     * Kicks off Google OAuth. Generates a fresh CSRF state, builds Google's authorization URL,
     * sets two short-lived cookies, returns a 302 to Google.
     */
    @GetMapping("/google/start")
    public ResponseEntity<Void> startGoogleLogin(
            @RequestParam(name = "redirect", required = false) String redirect) {

        String resolvedRedirect = resolveRedirect(redirect);
        String state = stateGenerator.generate();
        String authUrl = authUrlBuilder.build(state, /* forceConsent= */ true);

        ResponseCookie stateCookie = shortLivedOauthCookie(STATE_COOKIE, state);
        ResponseCookie postLoginCookie = shortLivedOauthCookie(POST_LOGIN_COOKIE, resolvedRedirect);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authUrl))
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .header(HttpHeaders.SET_COOKIE, postLoginCookie.toString())
                .build();
    }

    // ─── §4.2 — /google/callback (happy path) ──────────────────────────────

    /**
     * Handles Google's post-consent redirect. Verifies CSRF state, exchanges code for tokens,
     * extracts identity, persists Google tokens, mints session JWT, sets session + CSRF cookies,
     * 302s browser back to the SPA path stored at /start.
     */
    @GetMapping("/google/callback")
    public ResponseEntity<Void> handleGoogleCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            @CookieValue(name = STATE_COOKIE, required = false) String stateCookie,
            @CookieValue(name = POST_LOGIN_COOKIE, required = false) String postLoginCookie) {

        if (error != null && !error.isBlank()) {
            // Spec §4.3 first branch — user pressed Cancel at Google.
            throw new LoginCancelledException();
        }

        verifyCsrfState(state, stateCookie);

        GoogleTokenResponse tokens = googleClient.exchangeCode(code);
        IdentityClaims identity = idTokenReader.readClaims(tokens.idToken());

        // TODO(user-engine): POST /internal/users/upsert-from-google when user_engine exists.
        log.info(
                "Would upsert user_engine identity for sub={} email={}",
                identity.sub(), identity.email());

        // §4.2c — persist Google tokens (encrypted at rest via TokenCipher).
        loginTokenService.upsertFromGoogle(identity.sub(), tokens);

        String sessionJwt = jwtIssuer.issue(identity, extractScopes(tokens.scope()));
        String csrfToken = stateGenerator.generate();
        String redirectPath = resolveRedirect(postLoginCookie);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectPath))
                .header(HttpHeaders.SET_COOKIE, sessionCookie(sessionJwt).toString())
                .header(HttpHeaders.SET_COOKIE, csrfCookie(csrfToken).toString())
                .header(HttpHeaders.SET_COOKIE, clearedOauthCookie(STATE_COOKIE).toString())
                .header(HttpHeaders.SET_COOKIE, clearedOauthCookie(POST_LOGIN_COOKIE).toString())
                .build();
    }

    // ─── §4.4 — /logout ────────────────────────────────────────────────────

    /**
     * Idempotent client-side sign-out. Double-submit CSRF: the SPA reads the non-HttpOnly
     * {@code kk_csrf} cookie and echoes it as {@code X-CSRF-Token}. auth_engine confirms both
     * halves match. Clears both session cookies; DB untouched (JWTs are stateless).
     *
     * <p>Google refresh_token in {@code login_tokens} is deliberately preserved so the next
     * login on this browser reuses it silently — "logged out" ≠ "disconnected Gmail".
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = CSRF_COOKIE, required = false) String csrfCookieValue,
            @RequestHeader(name = CSRF_HEADER, required = false) String csrfHeaderValue) {

        verifyDoubleSubmitCsrf(csrfCookieValue, csrfHeaderValue);

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, clearedSessionCookie(SESSION_COOKIE).toString())
                .header(HttpHeaders.SET_COOKIE, clearedSessionCookie(CSRF_COOKIE).toString())
                .build();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    /**
     * Validates requested SPA return path — falls back to default on absolute URLs, schema-relative
     * URLs ({@code //evil.com/x}), or paths not in the allow-list.
     */
    private String resolveRedirect(String requested) {
        if (requested == null || requested.isBlank()) {
            return postLogin.defaultPath();
        }
        if (!requested.startsWith("/") || requested.startsWith("//")) {
            return postLogin.defaultPath();
        }
        return postLogin.isAllowed(requested) ? requested : postLogin.defaultPath();
    }

    /**
     * CSRF check for the OAuth callback — state param must match state cookie. Constant-time
     * compare to avoid timing-based state exfiltration.
     */
    private static void verifyCsrfState(String stateParam, String stateCookie) {
        if (stateParam == null || stateParam.isBlank()) {
            throw new CsrfMismatchException("state query param missing");
        }
        if (stateCookie == null || stateCookie.isBlank()) {
            throw new CsrfMismatchException("kk_oauth_state cookie missing");
        }
        if (!MessageDigest.isEqual(
                stateParam.getBytes(StandardCharsets.UTF_8),
                stateCookie.getBytes(StandardCharsets.UTF_8))) {
            throw new CsrfMismatchException("state mismatch");
        }
    }

    /**
     * CSRF check for logout — X-CSRF-Token header must equal kk_csrf cookie value. Both halves
     * required. Constant-time compare.
     */
    private static void verifyDoubleSubmitCsrf(String cookie, String header) {
        if (cookie == null || cookie.isBlank()) {
            throw new CsrfMismatchException("kk_csrf cookie missing");
        }
        if (header == null || header.isBlank()) {
            throw new CsrfMismatchException("X-CSRF-Token header missing");
        }
        if (!MessageDigest.isEqual(
                cookie.getBytes(StandardCharsets.UTF_8),
                header.getBytes(StandardCharsets.UTF_8))) {
            throw new CsrfMismatchException("CSRF cookie/header mismatch");
        }
    }

    /** Splits the space-separated Google scope string into a list. */
    private static List<String> extractScopes(String spaceSeparated) {
        if (spaceSeparated == null || spaceSeparated.isBlank()) return List.of();
        return Arrays.stream(spaceSeparated.trim().split("\\s+")).toList();
    }

    private static ResponseCookie shortLivedOauthCookie(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .sameSite("Lax")
                .path(OAUTH_COOKIE_PATH)
                .maxAge(OAUTH_COOKIE_TTL_SECONDS)
                .build();
    }

    /**
     * Long-lived session cookie carrying the RS256 JWT. {@code Secure} is off for local dev;
     * profile-aware config will flip it on in staging/prod.
     */
    private ResponseCookie sessionCookie(String jwt) {
        return ResponseCookie.from(SESSION_COOKIE, jwt)
                .httpOnly(true)
                .sameSite("Lax")
                .path(SESSION_COOKIE_PATH)
                .maxAge(jwtProps.expirationHours() * 3600L)
                .build();
    }

    /**
     * Companion CSRF cookie — deliberately NOT HttpOnly so the SPA can read it via
     * {@code document.cookie} and echo it back as a header on unsafe methods.
     */
    private ResponseCookie csrfCookie(String token) {
        return ResponseCookie.from(CSRF_COOKIE, token)
                .httpOnly(false)
                .sameSite("Lax")
                .path(SESSION_COOKIE_PATH)
                .maxAge(jwtProps.expirationHours() * 3600L)
                .build();
    }

    /** Zero-max-age cookie that instructs the browser to delete the named oauth cookie. */
    private static ResponseCookie clearedOauthCookie(String name) {
        return ResponseCookie.from(name, "").path(OAUTH_COOKIE_PATH).maxAge(0).build();
    }

    /** Zero-max-age cookie at root path — used for logout to clear kk_session / kk_csrf. */
    private static ResponseCookie clearedSessionCookie(String name) {
        return ResponseCookie.from(name, "").path(SESSION_COOKIE_PATH).maxAge(0).build();
    }
}
