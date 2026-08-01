package org.tb.khata.login.auth.gcp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.khata.login.auth.LoginTokenService;
import org.tb.khata.login.auth.config.RedirectAllowlistProperties;
import org.tb.khata.login.auth.exception.CsrfMismatchException;
import org.tb.khata.login.auth.exception.LoginCancelledException;
import org.tb.khata.login.auth.gcp.GoogleOAuthClient;
import org.tb.khata.login.auth.gcp.IdTokenClaimsReader;
import org.tb.khata.login.auth.gcp.dto.GoogleTokenResponse;
import org.tb.khata.login.auth.gcp.dto.IdentityClaims;
import org.tb.khata.login.security.config.JwtProperties;

/**
 * Unit test for {@link GoogleAuthCallbackService}.
 *
 * <p>Phase-2 note: session-JWT minting and CSRF cookie emission are currently commented out in the
 * callback — the flow lands users back on the SPA without a {@code kk_session} cookie. These tests
 * cover the surviving behaviour (CSRF state check, token persistence, oauth cookie clearing,
 * redirect). When JWT wiring is restored, re-add {@code jwtIssuer}/{@code stateGenerator} stubs
 * plus assertions on {@code kk_session} + {@code kk_csrf}.
 */
class GoogleAuthCallbackServiceTest {

    private GoogleOAuthClient googleClient;
    private IdTokenClaimsReader idTokenReader;
    private LoginTokenService loginTokenService;

    private GoogleAuthCallbackService service;

    @BeforeEach
    void setUp() {
        googleClient = mock(GoogleOAuthClient.class);
        idTokenReader = mock(IdTokenClaimsReader.class);
        loginTokenService = mock(LoginTokenService.class);

        RedirectionResolver resolver = new RedirectionResolver();
        ReflectionTestUtils.setField(
                resolver,
                "postLogin",
                new RedirectAllowlistProperties(
                        "/dashboard", List.of("/dashboard", "/expenses")));
        CookieCreator cookies = new CookieCreator();
        ReflectionTestUtils.setField(
                cookies, "jwtProps", new JwtProperties("/tmp", "v1", "iss", 24L));

        service = new GoogleAuthCallbackService();
        ReflectionTestUtils.setField(service, "googleClient", googleClient);
        ReflectionTestUtils.setField(service, "idTokenReader", idTokenReader);
        ReflectionTestUtils.setField(service, "loginTokenService", loginTokenService);
        ReflectionTestUtils.setField(service, "redirectionResolver", resolver);
        ReflectionTestUtils.setField(service, "cookieCreator", cookies);
        // jwtIssuer and stateGenerator fields left unset — their callers inside handleCallback are
        // commented out during phase 2. Restore stubs when the JWT branch is re-enabled.
    }

    // ─── §4.3 — failure branches short-circuit before Google is called ─────

    @Test
    void errorParamThrowsLoginCancelled() {
        assertThatThrownBy(
                        () -> service.handleCallback("access_denied", null, null, null, null))
                .isInstanceOf(LoginCancelledException.class);
        verifyNoInteractions(googleClient, idTokenReader, loginTokenService);
    }

    @Test
    void blankStateParamThrowsCsrfMismatch() {
        assertThatThrownBy(
                        () -> service.handleCallback(null, "  ", "cookie", "code", null))
                .isInstanceOf(CsrfMismatchException.class)
                .hasMessageContaining("state query param");
        verifyNoInteractions(googleClient);
    }

    @Test
    void missingStateCookieThrowsCsrfMismatch() {
        assertThatThrownBy(() -> service.handleCallback(null, "s", null, "code", null))
                .isInstanceOf(CsrfMismatchException.class)
                .hasMessageContaining("kk_oauth_state cookie");
        verifyNoInteractions(googleClient);
    }

    @Test
    void mismatchedStateAndCookieThrowsCsrfMismatch() {
        assertThatThrownBy(() -> service.handleCallback(null, "left", "right", "code", null))
                .isInstanceOf(CsrfMismatchException.class)
                .hasMessageContaining("state mismatch");
        verifyNoInteractions(googleClient);
    }

    // ─── §4.2 — happy path ────────────────────────────────────────────────

    @Test
    void happyPathPersistsTokensAndReturnsRedirectClearingOauthCookies() {
        GoogleTokenResponse tokens =
                new GoogleTokenResponse(
                        "goog-access",
                        "goog-refresh",
                        "header.payload.sig",
                        3600L,
                        "openid email gmail.readonly",
                        "Bearer");
        IdentityClaims identity =
                new IdentityClaims("sub-1", "tb@example.com", "TB", "https://pic");
        given(googleClient.exchangeCode("auth-code")).willReturn(tokens);
        given(idTokenReader.readClaims("header.payload.sig")).willReturn(identity);

        ResponseEntity<Void> response =
                service.handleCallback(null, "matching", "matching", "auth-code", "/expenses");

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/expenses");
        verify(loginTokenService).upsertFromGoogle("sub-1", tokens);

        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies)
                .anyMatch(c -> c.startsWith("kk_oauth_state=") && c.contains("Max-Age=0"))
                .anyMatch(c -> c.startsWith("kk_oauth_post_login=") && c.contains("Max-Age=0"))
                // Phase-2 gate: session + CSRF cookies must NOT appear while JWT branch commented.
                .noneMatch(c -> c.startsWith("kk_session="))
                .noneMatch(c -> c.startsWith("kk_csrf="));
    }

    @Test
    void nullPostLoginCookieFallsBackToDefaultRedirect() {
        given(googleClient.exchangeCode(anyString()))
                .willReturn(
                        new GoogleTokenResponse(
                                "a", "r", "h.p.s", 3600L, "openid", "Bearer"));
        given(idTokenReader.readClaims(anyString()))
                .willReturn(new IdentityClaims("s", "e@x", "n", "p"));

        ResponseEntity<Void> response = service.handleCallback(null, "s", "s", "code", null);

        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/dashboard");
    }

    @Test
    void nonAllowlistedPostLoginCookieFallsBackToDefaultRedirect() {
        given(googleClient.exchangeCode(anyString()))
                .willReturn(
                        new GoogleTokenResponse(
                                "a", "r", "h.p.s", 3600L, "openid", "Bearer"));
        given(idTokenReader.readClaims(anyString()))
                .willReturn(new IdentityClaims("s", "e@x", "n", "p"));

        ResponseEntity<Void> response =
                service.handleCallback(null, "s", "s", "code", "/not-allowed");

        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/dashboard");
    }
}
