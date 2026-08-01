package org.tb.khata.login.auth.gcp.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.khata.login.security.config.JwtProperties;

class CookieCreatorTest {

    private CookieCreator cookies;

    @BeforeEach
    void setUp() {
        cookies = new CookieCreator();
        ReflectionTestUtils.setField(
                cookies,
                "jwtProps",
                new JwtProperties("/tmp/ignored", "v1", "auth_engine", 24L));
    }

    @Test
    void sessionCookieIsHttpOnlyLaxAndScopedToRoot() {
        ResponseCookie c = cookies.sessionCookie("jwt-value");

        assertThat(c.getName()).isEqualTo("kk_session");
        assertThat(c.getValue()).isEqualTo("jwt-value");
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.getSameSite()).isEqualTo("Lax");
        assertThat(c.getPath()).isEqualTo("/");
        assertThat(c.getMaxAge().toSeconds()).isEqualTo(24L * 3600L);
    }

    @Test
    void csrfCookieIsReadableByBrowserForDoubleSubmit() {
        ResponseCookie c = cookies.csrfCookie("csrf-token");

        assertThat(c.getName()).isEqualTo("kk_csrf");
        assertThat(c.getValue()).isEqualTo("csrf-token");
        // Non-HttpOnly is deliberate — SPA must read via document.cookie and echo back as header.
        assertThat(c.isHttpOnly()).isFalse();
        assertThat(c.getSameSite()).isEqualTo("Lax");
        assertThat(c.getPath()).isEqualTo("/");
        assertThat(c.getMaxAge().toSeconds()).isEqualTo(24L * 3600L);
    }

    @Test
    void shortLivedOauthCookieScopedToAuthPathAndTenMinutes() {
        ResponseCookie c = cookies.shortLivedOauthCookie("kk_oauth_state", "abc123");

        assertThat(c.getName()).isEqualTo("kk_oauth_state");
        assertThat(c.getValue()).isEqualTo("abc123");
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.getSameSite()).isEqualTo("Lax");
        assertThat(c.getPath()).isEqualTo("/api/auth/");
        assertThat(c.getMaxAge().toSeconds()).isEqualTo(600L);
    }

    @Test
    void clearedOauthCookieHasZeroMaxAgeAndEmptyValue() {
        ResponseCookie c = cookies.clearedOauthCookie("kk_oauth_state");

        assertThat(c.getValue()).isEmpty();
        assertThat(c.getMaxAge().toSeconds()).isZero();
        assertThat(c.getPath()).isEqualTo("/api/auth/");
    }

    @Test
    void jwtExpirationHoursDrivesSessionCookieMaxAge() {
        CookieCreator custom = new CookieCreator();
        ReflectionTestUtils.setField(
                custom, "jwtProps", new JwtProperties("/tmp", "v1", "auth_engine", 48L));

        assertThat(custom.sessionCookie("x").getMaxAge().toSeconds()).isEqualTo(48L * 3600L);
        assertThat(custom.csrfCookie("x").getMaxAge().toSeconds()).isEqualTo(48L * 3600L);
    }
}
