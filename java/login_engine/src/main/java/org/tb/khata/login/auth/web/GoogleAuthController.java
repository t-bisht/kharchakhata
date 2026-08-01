package org.tb.khata.login.auth.web;

import java.net.URI;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tb.khata.login.auth.gcp.GoogleAuthUrlBuilder;
import org.tb.khata.login.auth.gcp.GoogleOAuthClient;
import org.tb.khata.login.auth.gcp.IdTokenClaimsReader;
import org.tb.khata.login.auth.OAuthStateGenerator;
import org.tb.khata.login.auth.SessionJwtIssuer;
import org.tb.khata.login.auth.config.JwtProperties;
import org.tb.khata.login.auth.config.RedirectAllowlistProperties;
import org.tb.khata.login.auth.dto.GoogleTokenResponse;
import org.tb.khata.login.auth.dto.IdentityClaims;
import org.tb.khata.login.auth.exception.CsrfMismatchException;
import org.tb.khata.login.auth.exception.LoginCancelledException;

/**
 * Browser-facing endpoints for the Google OAuth flow.
 *
 * <p>Implements spec §4.1 ({@code /google/start}) and §4.2 happy path ({@code /google/callback}).
 * Failure branches from §4.3 are handled by {@link AuthExceptionHandler}. Session-JWT refresh is
 * intentionally not implemented — spec picks Pattern A (full re-login on expiry, §4.10).
 *
 * <p>Cookies used across the two endpoints:
 *
 * <ul>
 *   <li>{@code kk_oauth_state} — set at {@code /start}, consumed at {@code /callback} for CSRF
 *   <li>{@code kk_oauth_post_login} — set at {@code /start}, consumed at {@code /callback} to
 *       route the user back to the SPA path they came from
 *   <li>{@code kk_session} — set at {@code /callback} (24 h RS256 JWT); the actual session token
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
    private final RedirectAllowlistProperties postLogin;
    private final JwtProperties jwtProps;

    public GoogleAuthController(
            OAuthStateGenerator stateGenerator,
            GoogleAuthUrlBuilder authUrlBuilder,
            GoogleOAuthClient googleClient,
            IdTokenClaimsReader idTokenReader,
            SessionJwtIssuer jwtIssuer,
            RedirectAllowlistProperties postLogin,
            JwtProperties jwtProps) {
        this.stateGenerator = stateGenerator;
        this.authUrlBuilder = authUrlBuilder;
        this.googleClient = googleClient;
        this.idTokenReader = idTokenReader;
        this.jwtIssuer = jwtIssuer;
        this.postLogin = postLogin;
        this.jwtProps = jwtProps;
    }

    // ─── §4.1 — /google/start ───────────────────────────────────────────────

    /**
     * Kicks off Google OAuth. Generates a fresh CSRF state, builds Google's authorization URL,
     * sets two short-lived cookies, returns a 302 to Google. Spec §4.1.
     *
     * @param redirect optional SPA path to send the user back to after login; validated against
     *     {@link RedirectAllowlistProperties#allowedPaths()}, otherwise falls back to default
     */
    @GetMapping("/google/start")
    public ResponseEntity<Void> startGoogleLogin(
            @RequestParam(name = "redirect", required = false) String redirect) {

        String resolvedRedirect = resolveRedirect(redirect);
        String state = stateGenerator.generate();
        String authUrl = authUrlBuilder.build(state, /* forceConsent= */ true);

        //these cookies are used to send to the browser so that they can be returned
        ResponseCookie stateCookie = shortLivedOauthCookie(STATE_COOKIE, state); //stores the state for verification
        ResponseCookie postLoginCookie = shortLivedOauthCookie(POST_LOGIN_COOKIE, resolvedRedirect);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authUrl))
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .header(HttpHeaders.SET_COOKIE, postLoginCookie.toString())
                .build();
    }

    // ─── §4.2 — /google/callback (happy path) ──────────────────────────────

    /**
     * Handles Google's post-consent redirect. Verifies CSRF state, exchanges the code for tokens,
     * extracts identity from the id_token, mints a session JWT, sets it as a cookie, and 302s the
     * browser to the caller-requested SPA path (or the default).
     *
     * <p>Deferred to later sub-milestones:
     *
     * <ul>
     *   <li>4.2c — persistence in {@code login_tokens}
     *   <li>4.2d — {@code user_engine} upsert (currently logged as TODO)
     *   <li>4.4 — {@code kk_csrf} companion cookie (needed only at logout)
     * </ul>
     *
     * @param code the authorization code returned by Google
     * @param state the state string Google echoes back (must equal our cookie)
     * @param error {@code "access_denied"} when the user cancels; otherwise absent
     * @param stateCookie value of {@link #STATE_COOKIE} (required unless {@code error} is present)
     * @param postLoginCookie value of {@link #POST_LOGIN_COOKIE} (optional; defaults to configured
     *     default path)
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

        // TODO(user-engine): call POST /internal/users/upsert-from-google when user_engine exists.
        log.info("Would upsert user_engine identity for sub={} email={}", identity.sub(), identity.email());

        // TODO(4.2c): persist Google tokens in login_tokens (encrypted). Deferred.

        String sessionJwt = jwtIssuer.issue(identity, extractScopes(tokens.scope()));
        String redirectPath = resolveRedirect(postLoginCookie);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectPath))
                .header(HttpHeaders.SET_COOKIE, sessionCookie(sessionJwt).toString())
                .header(HttpHeaders.SET_COOKIE, clearedOauthCookie(STATE_COOKIE).toString())
                .header(HttpHeaders.SET_COOKIE, clearedOauthCookie(POST_LOGIN_COOKIE).toString())
                .build();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    /**
     * Validates the requested SPA return path. Falls back to {@link
     * RedirectAllowlistProperties#defaultPath()} on anything that isn't an explicit allow-listed
     * relative path — including absolute URLs and schema-relative URLs like {@code //evil.com/x}.
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
     * Rejects the callback if the state param or the state cookie is missing, or if they don't
     * match. Uses {@link MessageDigest#isEqual} for constant-time comparison — avoids timing-based
     * exfiltration of state values.
     */
    private static void verifyCsrfState(String stateParam, String stateCookie) {
        if (stateParam == null || stateParam.isBlank()) {
            throw new CsrfMismatchException("state query param missing");
        }
        if (stateCookie == null || stateCookie.isBlank()) {
            throw new CsrfMismatchException("kk_oauth_state cookie missing");
        }
        byte[] a = stateParam.getBytes(StandardCharsets.UTF_8);
        byte[] b = stateCookie.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(a, b)) {
            throw new CsrfMismatchException("state mismatch");
        }
    }

    /** Splits the space-separated Google scope string into a list. Never returns null. */
    private static List<String> extractScopes(String spaceSeparated) {
        if (spaceSeparated == null || spaceSeparated.isBlank()) {
            return List.of();
        }
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
     * Builds the long-lived session cookie. {@code Secure} is deliberately left off for local
     * dev — a profile-aware config will add it in {@code prod}. HttpOnly + SameSite=Lax stay on
     * everywhere.
     */
    private ResponseCookie sessionCookie(String jwt) {
        return ResponseCookie.from(SESSION_COOKIE, jwt)
                .httpOnly(true)
                .sameSite("Lax")
                .path(SESSION_COOKIE_PATH)
                .maxAge(jwtProps.expirationHours() * 3600L)
                .build();
    }

    /** Zero-max-age cookie that instructs the browser to delete the named oauth cookie. */
    private static ResponseCookie clearedOauthCookie(String name) {
        return ResponseCookie.from(name, "").path(OAUTH_COOKIE_PATH).maxAge(0).build();
    }
}
