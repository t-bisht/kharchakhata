package org.tb.khata.login.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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
import org.tb.khata.login.auth.GoogleAuthUrlBuilder;
import org.tb.khata.login.auth.OAuthStateGenerator;
import org.tb.khata.login.auth.config.RedirectAllowlistProperties;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {AuthController.class, AuthControllerTest.TestConfig.class})
class AuthControllerTest {

    @Configuration
    static class TestConfig {
        @Bean
        RedirectAllowlistProperties postLogin() {
            return new RedirectAllowlistProperties(
                    "/dashboard", List.of("/dashboard", "/expenses", "/settings"));
        }
    }

    @Autowired MockMvc mvc;
    @MockBean OAuthStateGenerator stateGenerator;
    @MockBean GoogleAuthUrlBuilder urlBuilder;

    @Test
    void redirectsToGoogleAndSetsBothCookies() throws Exception {
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
    void honoursAllowlistedRedirectParam() throws Exception {
        given(stateGenerator.generate()).willReturn("s");
        given(urlBuilder.build(anyString(), anyBoolean())).willReturn("https://google/");

        ResultActions r = mvc.perform(get("/api/auth/google/start").param("redirect", "/expenses"));

        assertThat(r.andReturn().getResponse().getHeaders("Set-Cookie"))
                .anyMatch(c -> c.startsWith("kk_oauth_post_login=/expenses"));
    }

    @Test
    void fallsBackToDefaultForAbsoluteUrl() throws Exception {
        given(stateGenerator.generate()).willReturn("s");
        given(urlBuilder.build(anyString(), anyBoolean())).willReturn("https://google/");

        ResultActions r =
                mvc.perform(get("/api/auth/google/start").param("redirect", "https://evil.com/x"));

        assertThat(r.andReturn().getResponse().getHeaders("Set-Cookie"))
                .anyMatch(c -> c.startsWith("kk_oauth_post_login=/dashboard"));
    }

    @Test
    void fallsBackToDefaultForSchemaRelativeUrl() throws Exception {
        given(stateGenerator.generate()).willReturn("s");
        given(urlBuilder.build(anyString(), anyBoolean())).willReturn("https://google/");

        ResultActions r =
                mvc.perform(get("/api/auth/google/start").param("redirect", "//evil.com/x"));

        assertThat(r.andReturn().getResponse().getHeaders("Set-Cookie"))
                .anyMatch(c -> c.startsWith("kk_oauth_post_login=/dashboard"));
    }

    @Test
    void fallsBackToDefaultForUnknownPath() throws Exception {
        given(stateGenerator.generate()).willReturn("s");
        given(urlBuilder.build(anyString(), anyBoolean())).willReturn("https://google/");

        ResultActions r =
                mvc.perform(get("/api/auth/google/start").param("redirect", "/does-not-exist"));

        assertThat(r.andReturn().getResponse().getHeaders("Set-Cookie"))
                .anyMatch(c -> c.startsWith("kk_oauth_post_login=/dashboard"));
    }

    private static org.springframework.test.web.servlet.result.HeaderResultMatchers header() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.header();
    }

    private static org.springframework.test.web.servlet.result.StatusResultMatchers status() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.status();
    }
}
