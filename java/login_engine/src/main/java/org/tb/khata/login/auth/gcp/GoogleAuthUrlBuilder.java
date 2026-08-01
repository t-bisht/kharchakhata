package org.tb.khata.login.auth.gcp;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.tb.khata.login.auth.config.GoogleOAuthProperties;

@Component
public class GoogleAuthUrlBuilder {

    private final GoogleOAuthProperties props;

    public GoogleAuthUrlBuilder(GoogleOAuthProperties props) {
        this.props = props;
    }

    public String build(String state, boolean forceConsent) {
        UriComponentsBuilder b =
                UriComponentsBuilder.fromUriString(props.authUri())
                        .queryParam("client_id", props.clientId())
                        .queryParam("redirect_uri", props.redirectUri())
                        .queryParam("response_type", "code")
                        .queryParam("scope", String.join(" ", props.scopes()))
                        .queryParam("state", state)
                        .queryParam("access_type", "offline")
                        .queryParam("include_granted_scopes", "true");
        if (forceConsent) {
            b.queryParam("prompt", "consent");
        }
        return b.encode().build().toUriString();
    }
}
