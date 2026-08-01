package org.tb.khata.login.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.tb.khata.login.auth.LoginTokenService;
import org.tb.khata.login.auth.OAuthStateGenerator;
import org.tb.khata.login.auth.SessionJwtIssuer;
import org.tb.khata.login.auth.config.JwtProperties;
import org.tb.khata.login.auth.config.RedirectAllowlistProperties;
import org.tb.khata.login.auth.dto.GoogleTokenResponse;
import org.tb.khata.login.auth.dto.IdentityClaims;
import org.tb.khata.login.auth.gcp.GoogleAuthUrlBuilder;
import org.tb.khata.login.auth.gcp.GoogleOAuthClient;
import org.tb.khata.login.auth.gcp.IdTokenClaimsReader;

@WebMvcTest(controllers = GoogleAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(
        classes = {
            GoogleAuthController.class,
            AuthExceptionHandler.class,
            GoogleAuthControllerTest.TestConfig.class
        })
class GoogleAuthControllerTest {

    @Configuration
    static class TestConfig {
        @Bean
        RedirectAllowlistProperties postLogin() {
            return new RedirectAllowlistProperties(
                    "/dashboard", List.of("/dashboard", "/expenses", "/settings"));
        }

        @Bean
        JwtProperties jwtProps() {
            return new JwtProperties("/tmp/ignored", "v1", "auth_engine", 24L);
        }
    }

    @Autowired MockMvc mvc;
    @MockBean OAuthStateGenerator stateGenerator;
    @MockBean GoogleAuthUrlBuilder urlBuilder;
    @MockBean GoogleOAuthClient googleClient;
    @MockBean IdTokenClaimsReader idTokenReader;
    @MockBean SessionJwtIssuer jwtIssuer;
    @MockBean LoginTokenService loginTokenService;

    // ─── /google/start ────────────────────────────────────────────────────

    @Test
    void startRedirectsToGoogleAndSetsBothCookies() throws Exception {
        given(stateGenerator.generate()).willReturn("fixed-state");
        given(urlBuilder.build(anyString(), anyBoolean()))
                .willReturn("https://accounts.google.com/o/oauth2/v2/auth?state=fixed-state");

        ResultActions r = mvc.perform(get("/api/auth/google/start"));

        r.andExpect(status().is(302))
                .andExpect(
                        header().string(
                                        "Location",
                                        "https://accounts.google.com/o/oauth2/v2/auth?state=fixed-state"));

        List<String> setCookies = r.andReturn().getResponse().getHeaders("Set-Cookie");
        assertThat(setCookies)
                .anyMatch(c -> c.startsWith("kk_oauth_state=fixed-state"))
                .anyMatch(c -> c.startsWith("kk_oauth_post_login=/dashboard"))
                .allMatch(c -> c.contains("HttpOnly"))
                .allMatch(c -> c.contains("SameSite=Lax"))
                .allMatch(c -> c.contains("Path=/api/auth/"))
                .allMatch(c -> c.contains("Max-Age=600"));
    }

    @Test
    void startHonoursAllowlistedRedirect() throws Exception {
        given(stateGenerator.generate()).willReturn("s");
        given(urlBuilder.build(anyString(), anyBoolean())).willReturn("https://google/");

        ResultActions r = mvc.perform(get("/api/auth/google/start").param("redirect", "/expenses"));

        assertThat(r.andReturn().getResponse().getHeaders("Set-Cookie"))
                .anyMatch(c -> c.startsWith("kk_oauth_post_login=/expenses"));
    }

    @Test
    void startFallsBackForAbsoluteUrl() throws Exception {
        given(stateGenerator.generate()).willReturn("s");
        given(urlBuilder.build(anyString(), anyBoolean())).willReturn("https://google/");

        ResultActions r =
                mvc.perform(get("/api/auth/google/start").param("redirect", "https://evil.com/x"));

        assertThat(r.andReturn().getResponse().getHeaders("Set-Cookie"))
                .anyMatch(c -> c.startsWith("kk_oauth_post_login=/dashboard"));
    }

    // ─── /google/callback ────────────────────────────────────────────────

    @Test
    void callbackHappyPathMintsSessionCookieAndRedirectsToPostLogin() throws Exception {
        given(googleClient.exchangeCode("auth-code"))
                .willReturn(
                        new GoogleTokenResponse(
                                "goog-access",
                                "goog-refresh",
                                "header.payload.sig",
                                3600L,
                                "openid email gmail.readonly",
                                "Bearer"));
        given(idTokenReader.readClaims("header.payload.sig"))
                .willReturn(new IdentityClaims("sub-1", "tb@example.com", "TB", "https://pic"));
        given(jwtIssuer.issue(any(), any())).willReturn("session.jwt.here");

        ResultActions r =
                mvc.perform(
                        get("/api/auth/google/callback")
                                .param("code", "auth-code")
                                .param("state", "matching")
                                .cookie(new Cookie("kk_oauth_state", "matching"))
                                .cookie(new Cookie("kk_oauth_post_login", "/expenses")));

        r.andExpect(status().is(302)).andExpect(header().string("Location", "/expenses"));

        List<String> cookies = r.andReturn().getResponse().getHeaders("Set-Cookie");
        assertThat(cookies)
                .anyMatch(c -> c.startsWith("kk_session=session.jwt.here") && c.contains("HttpOnly"))
                .anyMatch(c -> c.startsWith("kk_oauth_state=") && c.contains("Max-Age=0"))
                .anyMatch(c -> c.startsWith("kk_oauth_post_login=") && c.contains("Max-Age=0"));
    }

    @Test
    void callbackWithMismatchedStateRedirectsToStateInvalid() throws Exception {
        ResultActions r =
                mvc.perform(
                        get("/api/auth/google/callback")
                                .param("code", "c")
                                .param("state", "wrong")
                                .cookie(new Cookie("kk_oauth_state", "right")));

        r.andExpect(status().is(302))
                .andExpect(header().string("Location", "/login?err=state_invalid"));
    }

    @Test
    void callbackWithMissingStateCookieRedirectsToStateInvalid() throws Exception {
        ResultActions r =
                mvc.perform(
                        get("/api/auth/google/callback").param("code", "c").param("state", "s"));

        r.andExpect(status().is(302))
                .andExpect(header().string("Location", "/login?err=state_invalid"));
    }

    @Test
    void callbackWithAccessDeniedRedirectsToAccessDenied() throws Exception {
        ResultActions r =
                mvc.perform(get("/api/auth/google/callback").param("error", "access_denied"));

        r.andExpect(status().is(302))
                .andExpect(header().string("Location", "/login?err=access_denied"));
    }

    // ─── §4.4 — /logout ─────────────────────────────────────────────────

    @Test
    void logoutWithMatchingCsrfClearsCookies() throws Exception {
        ResultActions r =
                mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/auth/logout")
                                .cookie(new Cookie("kk_csrf", "csrf-abc"))
                                .header("X-CSRF-Token", "csrf-abc"));

        r.andExpect(status().is(204));

        List<String> cookies = r.andReturn().getResponse().getHeaders("Set-Cookie");
        assertThat(cookies)
                .anyMatch(c -> c.startsWith("kk_session=") && c.contains("Max-Age=0"))
                .anyMatch(c -> c.startsWith("kk_csrf=") && c.contains("Max-Age=0"));
    }

    @Test
    void logoutWithMismatchedCsrfReturnsCsrfMismatchRedirect() throws Exception {
        ResultActions r =
                mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/auth/logout")
                                .cookie(new Cookie("kk_csrf", "one"))
                                .header("X-CSRF-Token", "different"));

        // CsrfMismatchException maps via AuthExceptionHandler to /login?err=state_invalid
        r.andExpect(status().is(302))
                .andExpect(header().string("Location", "/login?err=state_invalid"));
    }
}
