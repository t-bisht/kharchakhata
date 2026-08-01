package org.tb.khata.login.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;
import org.tb.khata.login.auth.config.GoogleOAuthProperties;

class GoogleAuthUrlBuilderTest {

    private final GoogleOAuthProperties props =
            new GoogleOAuthProperties(
                    "client-abc.apps.googleusercontent.com",
                    "GOCSPX-secret",
                    "https://accounts.google.com/o/oauth2/v2/auth",
                    "https://oauth2.googleapis.com/token",
                    "http://localhost:3000/api/auth/google/callback",
                    List.of(
                            "openid",
                            "email",
                            "profile",
                            "https://www.googleapis.com/auth/gmail.readonly"));

    private final GoogleAuthUrlBuilder builder = new GoogleAuthUrlBuilder(props);

    @Test
    void urlContainsAllRequiredOauthParams() {
        String url = builder.build("state-xyz", true);
        Map<String, String> params = params(url);

        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
        assertThat(params)
                .containsEntry("client_id", "client-abc.apps.googleusercontent.com")
                .containsEntry(
                        "redirect_uri", "http://localhost:3000/api/auth/google/callback")
                .containsEntry("response_type", "code")
                .containsEntry(
                        "scope",
                        "openid email profile"
                                + " https://www.googleapis.com/auth/gmail.readonly")
                .containsEntry("state", "state-xyz")
                .containsEntry("access_type", "offline")
                .containsEntry("include_granted_scopes", "true")
                .containsEntry("prompt", "consent");
    }

    @Test
    void omitsPromptWhenNotForcingConsent() {
        String url = builder.build("s", false);
        assertThat(params(url)).doesNotContainKey("prompt");
    }

    private static Map<String, String> params(String url) {
        return UriComponentsBuilder.fromUri(URI.create(url)).build().getQueryParams().entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e ->
                                        URLDecoder.decode(
                                                e.getValue().get(0), StandardCharsets.UTF_8)));
    }
}
