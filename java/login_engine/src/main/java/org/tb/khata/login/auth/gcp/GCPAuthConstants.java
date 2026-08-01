package org.tb.khata.login.auth.gcp;

public interface GCPAuthConstants {
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
}
