package org.tb.khata.login.auth.gcp.contollers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tb.khata.login.auth.gcp.services.GoogleAuthCallbackService;
import org.tb.khata.login.auth.gcp.services.GoogleAuthStartService;

import static org.tb.khata.login.auth.gcp.GCPAuthConstants.POST_LOGIN_COOKIE;
import static org.tb.khata.login.auth.gcp.GCPAuthConstants.STATE_COOKIE;

/**
 * Browser-facing endpoints for the Google OAuth flow.
 *
 * <p>Implements spec §4.1 ({@code /google/start}) and §4.2 happy path ({@code /google/callback}).
 * Failure branches from §4.3 are handled by {@link AuthExceptionHandler}. Session-JWT refresh is
 * not implemented — spec picks Pattern A (full re-login on expiry, §4.10). The §4.4 logout endpoint
 * is intentionally not wired yet; see {@code GoogleLogoutService} for planned shape.
 *
 * <p>All cookie work lives in {@code CookieCreator} — session, CSRF, and the two short-lived OAuth
 * cookies are all set inside {@code GoogleAuthStartService} / {@code GoogleAuthCallbackService}.
 */
@RestController
@RequestMapping("/api/auth")
public class GoogleAuthController {

    private final GoogleAuthCallbackService authCallBackService;
    private final GoogleAuthStartService authStartService;

    public GoogleAuthController(
            GoogleAuthCallbackService authCallBackService,
            GoogleAuthStartService authStartService) {
        this.authCallBackService = authCallBackService;
        this.authStartService = authStartService;
    }

    // ─── §4.1 — /google/start ───────────────────────────────────────────────

    /**
     * Kicks off Google OAuth. Generates a fresh CSRF state, builds Google's authorization URL,
     * sets two short-lived cookies, returns a 302 to Google.
     */
    @GetMapping("/google/start")
    public ResponseEntity<Void> startGoogleLogin(
            @RequestParam(name = "redirect", required = false) String redirect) {

        return authStartService.startGoogleAuthService(redirect);
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

        return authCallBackService.handleCallback(error, state, stateCookie, code, postLoginCookie);
    }


}
