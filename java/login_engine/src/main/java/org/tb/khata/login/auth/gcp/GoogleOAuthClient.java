package org.tb.khata.login.auth.gcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.tb.khata.login.auth.config.GoogleOAuthProperties;
import org.tb.khata.login.auth.dto.GoogleTokenResponse;
import org.tb.khata.login.auth.exception.GoogleTokenExchangeFailedException;

/**
 * HTTP client for Google's OAuth token endpoint.
 *
 * <p>Only responsibility: POST an authorization code to Google's {@code /token} endpoint using
 * {@code grant_type=authorization_code} and return the parsed response. Refresh flow (spec §4.7)
 * will add a second method on this class later.
 *
 * <p>Uses Spring's {@link RestClient} (synchronous, Spring 6.1+). Timeouts and retries rely on
 * defaults for now — tune when we see real Google flakiness.
 */
@Component
public class GoogleOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);

    private final GoogleOAuthProperties props;
    private final RestClient restClient;

    public GoogleOAuthClient(GoogleOAuthProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder.build();
    }

    /**
     * Exchanges an authorization code for Google's token set (access + refresh + id_token).
     *
     * @param code the {@code ?code=...} value Google sent to our callback
     * @return parsed token response
     * @throws GoogleTokenExchangeFailedException if Google responds non-2xx or the request fails
     */
    public GoogleTokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("redirect_uri", props.redirectUri());
        form.add("grant_type", "authorization_code");

        try {
            GoogleTokenResponse body =
                    restClient
                            .post()
                            .uri(props.tokenUri())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .accept(MediaType.APPLICATION_JSON)
                            .body(form)
                            .retrieve()
                            .body(GoogleTokenResponse.class);

            if (body == null || body.accessToken() == null || body.idToken() == null) {
                throw new GoogleTokenExchangeFailedException(
                        "Google /token response missing access_token or id_token");
            }
            return body;
        } catch (RestClientResponseException e) {
            log.warn(
                    "Google /token exchange failed: status={}, body={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw new GoogleTokenExchangeFailedException(
                    "Google /token responded " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.warn("Google /token exchange failed: {}", e.getMessage());
            throw new GoogleTokenExchangeFailedException("Google /token unreachable", e);
        }
    }
}
