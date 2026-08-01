package org.tb.khata.login.auth.gcp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.tb.khata.login.auth.OAuthStateGenerator;
import org.tb.khata.login.auth.SessionJwtIssuer;
import org.tb.khata.login.auth.config.JwtProperties;
import org.tb.khata.login.auth.config.RedirectAllowlistProperties;
import org.tb.khata.login.auth.exception.CsrfMismatchException;
import org.tb.khata.login.auth.exception.LoginCancelledException;
import org.tb.khata.login.auth.gcp.GoogleOAuthClient;
import org.tb.khata.login.auth.gcp.IdTokenClaimsReader;
import org.tb.khata.login.auth.gcp.dto.GoogleTokenResponse;
import org.tb.khata.login.auth.gcp.dto.IdentityClaims;

class GoogleAuthCallbackServiceTest {

    private GoogleOAuthClient googleClient;
    private IdTokenClaimsReader idTokenReader;
    private LoginTokenService loginTokenService;
    private SessionJwtIssuer jwtIssuer;
    private OAuthStateGenerator stateGenerator;

    private GoogleAuthCallbackService service;

    @BeforeEach
    void setUp() {
        googleClient = mock(GoogleOAuthClient.class);
        idTokenReader = mock(IdTokenClaimsReader.class);
        loginTokenService = mock(LoginTokenService.class);
        jwtIssuer = mock(SessionJwtIssuer.class);
        stateGenerator = mock(OAuthStateGenerator.class);

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
        ReflectionTestUtils.setField(service, "jwtIssuer", jwtIssuer);
        ReflectionTestUtils.setField(service, "stateGenerator", stateGenerator);
        ReflectionTestUtils.setField(service, "redirectionResolver", resolver);
        ReflectionTestUtils.setField(service, "cookieCreator", cookies);
    }

    // ─── §4.3 — failure branches short-circuit before Google is called ─────

    @Test
    void errorParamThrowsLoginCancelled() {
        assertThatThrownBy(
                        () -> service.handleCallback("access_denied", null, null, null, null))
                .isInstanceOf(LoginCancelledException.class);
        verifyNoInteractions(googleClient, idTokenReader, loginTokenService, jwtIssuer);
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
    void happyPathPersistsTokensMintsJwtAndReturnsRedirectWithCookies() {
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
        given(jwtIssuer.issue(any(), any())).willReturn("session.jwt.here");
        given(stateGenerator.generate()).willReturn("csrf-abc");

        ResponseEntity<Void> response =
                service.handleCallback(null, "matching", "matching", "auth-code", "/expenses");

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/expenses");
        verify(loginTokenService).upsertFromGoogle("sub-1", tokens);

        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies)
                .anyMatch(
                        c ->
                                c.startsWith("kk_session=session.jwt.here")
                                        && c.contains("HttpOnly"))
                .anyMatch(c -> c.startsWith("kk_csrf=csrf-abc"))
                .anyMatch(c -> c.startsWith("kk_oauth_state=") && c.contains("Max-Age=0"))
                .anyMatch(c -> c.startsWith("kk_oauth_post_login=") && c.contains("Max-Age=0"));
    }

    @Test
    void scopesAreSplitAndForwardedToJwtIssuer() {
        given(googleClient.exchangeCode(anyString()))
                .willReturn(
                        new GoogleTokenResponse(
                                "a",
                                "r",
                                "h.p.s",
                                3600L,
                                "openid gmail.readonly profile",
                                "Bearer"));
        given(idTokenReader.readClaims(anyString()))
                .willReturn(new IdentityClaims("s", "e@x", "n", "p"));
        given(jwtIssuer.issue(any(), any())).willReturn("jwt");
        given(stateGenerator.generate()).willReturn("c");

        service.handleCallback(null, "s", "s", "code", null);

        verify(jwtIssuer)
                .issue(any(), eq(List.of("openid", "gmail.readonly", "profile")));
    }

    @Test
    void nullPostLoginCookieFallsBackToDefaultRedirect() {
        given(googleClient.exchangeCode(anyString()))
                .willReturn(
                        new GoogleTokenResponse(
                                "a", "r", "h.p.s", 3600L, "openid", "Bearer"));
        given(idTokenReader.readClaims(anyString()))
                .willReturn(new IdentityClaims("s", "e@x", "n", "p"));
        given(jwtIssuer.issue(any(), any())).willReturn("jwt");
        given(stateGenerator.generate()).willReturn("c");

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
        given(jwtIssuer.issue(any(), any())).willReturn("jwt");
        given(stateGenerator.generate()).willReturn("c");

        ResponseEntity<Void> response =
                service.handleCallback(null, "s", "s", "code", "/not-allowed");

        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/dashboard");
    }
}
