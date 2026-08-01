package org.tb.khata.login.auth.gcp.services;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.tb.khata.login.security.config.JwtProperties;

import static org.tb.khata.login.auth.gcp.GCPAuthConstants.*;

@Component
public class CookieCreator {
    /**
     * Long-lived session cookie carrying the RS256 JWT. {@code Secure} is off for local dev;
     * profile-aware config will flip it on in staging/prod.
     */

    @Autowired
    JwtProperties jwtProps;

    public ResponseCookie sessionCookie(String jwt) {
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
    public ResponseCookie csrfCookie(String token) {
        return ResponseCookie.from(CSRF_COOKIE, token)
                .httpOnly(false)
                .sameSite("Lax")
                .path(SESSION_COOKIE_PATH)
                .maxAge(jwtProps.expirationHours() * 3600L)
                .build();
    }


    public ResponseCookie shortLivedOauthCookie(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .sameSite("Lax")
                .path(OAUTH_COOKIE_PATH)
                .maxAge(OAUTH_COOKIE_TTL_SECONDS)
                .build();
    }

    /**
     * Zero-max-age cookie that instructs the browser to delete the named oauth cookie.
     */
    public ResponseCookie clearedOauthCookie(String name) {
        return ResponseCookie.from(name, "").path(OAUTH_COOKIE_PATH).maxAge(0).build();
    }

    /**
     * Zero-max-age cookie at root path — used for logout to clear kk_session / kk_csrf.
     */
    private ResponseCookie clearedSessionCookie(String name) {
        return ResponseCookie.from(name, "").path(SESSION_COOKIE_PATH).maxAge(0).build();
    }
}
