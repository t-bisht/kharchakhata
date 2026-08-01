package org.tb.khata.login.auth.web;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tb.khata.login.auth.GoogleAuthUrlBuilder;
import org.tb.khata.login.auth.OAuthStateGenerator;
import org.tb.khata.login.auth.config.RedirectAllowlistProperties;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String STATE_COOKIE = "kk_oauth_state";
    static final String POST_LOGIN_COOKIE = "kk_oauth_post_login";
    static final String COOKIE_PATH = "/api/auth/";
    static final long OAUTH_COOKIE_TTL_SECONDS = 600L;

    private final OAuthStateGenerator stateGenerator;
    private final GoogleAuthUrlBuilder authUrlBuilder;
    private final RedirectAllowlistProperties postLogin;

    public AuthController(
            OAuthStateGenerator stateGenerator,
            GoogleAuthUrlBuilder authUrlBuilder,
            RedirectAllowlistProperties postLogin) {
        this.stateGenerator = stateGenerator;
        this.authUrlBuilder = authUrlBuilder;
        this.postLogin = postLogin;
    }

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

    private String resolveRedirect(String requested) {
        if (requested == null || requested.isBlank()) {
            return postLogin.defaultPath();
        }
        // Reject absolute URLs and schema-less absolute URLs ("//evil.com/x").
        if (!requested.startsWith("/") || requested.startsWith("//")) {
            return postLogin.defaultPath();
        }
        return postLogin.isAllowed(requested) ? requested : postLogin.defaultPath();
    }

    private static ResponseCookie shortLivedOauthCookie(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(OAUTH_COOKIE_TTL_SECONDS)
                .build();
    }
}
