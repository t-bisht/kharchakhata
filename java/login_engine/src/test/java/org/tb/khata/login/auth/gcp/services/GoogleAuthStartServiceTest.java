package org.tb.khata.login.auth.gcp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.khata.login.auth.OAuthStateGenerator;
import org.tb.khata.login.auth.config.JwtProperties;
import org.tb.khata.login.auth.config.RedirectAllowlistProperties;
import org.tb.khata.login.auth.gcp.GoogleAuthUrlBuilder;

class GoogleAuthStartServiceTest {

    private OAuthStateGenerator stateGenerator;
    private GoogleAuthUrlBuilder urlBuilder;
    private GoogleAuthStartService service;

    @BeforeEach
    void setUp() {
        stateGenerator = mock(OAuthStateGenerator.class);
        urlBuilder = mock(GoogleAuthUrlBuilder.class);

        RedirectionResolver resolver = new RedirectionResolver();
        ReflectionTestUtils.setField(
                resolver,
                "postLogin",
                new RedirectAllowlistProperties(
                        "/dashboard", List.of("/dashboard", "/expenses")));
        CookieCreator cookies = new CookieCreator();
        ReflectionTestUtils.setField(
                cookies, "jwtProps", new JwtProperties("/tmp", "v1", "iss", 24L));

        service = new GoogleAuthStartService();
        ReflectionTestUtils.setField(service, "redirectionResolver", resolver);
        ReflectionTestUtils.setField(service, "stateGenerator", stateGenerator);
        ReflectionTestUtils.setField(service, "authUrlBuilder", urlBuilder);
        ReflectionTestUtils.setField(service, "cookieCreator", cookies);
    }

    @Test
    void redirects302ToGoogleAndSetsStateAndPostLoginCookies() {
        given(stateGenerator.generate()).willReturn("state-xyz");
        given(urlBuilder.build("state-xyz", true))
                .willReturn("https://accounts.google.com/o/oauth2/v2/auth?state=state-xyz");

        ResponseEntity<Void> response = service.startGoogleAuthService("/expenses");

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation().toString())
                .isEqualTo("https://accounts.google.com/o/oauth2/v2/auth?state=state-xyz");

        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies)
                .anyMatch(c -> c.startsWith("kk_oauth_state=state-xyz"))
                .anyMatch(c -> c.startsWith("kk_oauth_post_login=/expenses"))
                .allMatch(c -> c.contains("HttpOnly"))
                .allMatch(c -> c.contains("Path=/api/auth/"))
                .allMatch(c -> c.contains("Max-Age=600"));
    }

    @Test
    void redirectNotOnAllowlistFallsBackToDefaultPath() {
        given(stateGenerator.generate()).willReturn("s");
        given(urlBuilder.build("s", true)).willReturn("https://google/");

        ResponseEntity<Void> response = service.startGoogleAuthService("/not-on-list");

        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(c -> c.startsWith("kk_oauth_post_login=/dashboard"));
    }

    @Test
    void nullRedirectFallsBackToDefaultPath() {
        given(stateGenerator.generate()).willReturn("s");
        given(urlBuilder.build("s", true)).willReturn("https://google/");

        ResponseEntity<Void> response = service.startGoogleAuthService(null);

        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(c -> c.startsWith("kk_oauth_post_login=/dashboard"));
    }

    @Test
    void alwaysForcesConsentPromptOnAuthUrl() {
        given(stateGenerator.generate()).willReturn("s");
        given(urlBuilder.build("s", true)).willReturn("https://google/");

        service.startGoogleAuthService(null);

        // Spec §4.1 — force consent so Google always returns refresh_token.
        verify(urlBuilder).build("s", true);
    }
}
