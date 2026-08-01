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
import org.tb.khata.login.auth.exception.GoogleAuthRevokedException;
import org.tb.khata.login.auth.exception.GoogleTokenExchangeFailedException;
import org.tb.khata.login.auth.exception.UpstreamUnavailableException;

/**
 * HTTP client for Google's OAuth token endpoint. Handles both:
 *
 * <ul>
 *   <li>{@link #exchangeCode(String)} — initial code-for-tokens exchange (spec §4.2)
 *   <li>{@link #refresh(String)} — swap refresh_token for a new access_token (spec §4.7)
 * </ul>
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

    /**
     * Uses a stored refresh_token to mint a fresh access_token. Google usually keeps the same
     * refresh_token but occasionally rotates it — the caller must persist whatever comes back.
     *
     * @param refreshToken the previously-stored Google refresh_token
     * @return parsed token response ({@code access_token} + {@code expires_in} always populated;
     *     {@code refresh_token} present only when rotated)
     * @throws GoogleAuthRevokedException on {@code 400 invalid_grant} — user revoked or Google
     *     invalidated the grant; recovery requires full re-consent
     * @throws UpstreamUnavailableException on other Google errors or network failure
     */
    public GoogleTokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");

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
            if (body == null || body.accessToken() == null) {
                throw new UpstreamUnavailableException(
                        "Google /token refresh missing access_token", null);
            }
            return body;
        } catch (RestClientResponseException e) {
            // Google returns 400 { error: "invalid_grant" } for revoked / invalid refresh tokens.
            String responseBody = e.getResponseBodyAsString();
            if (e.getStatusCode().value() == 400 && responseBody.contains("invalid_grant")) {
                throw new GoogleAuthRevokedException("Google returned invalid_grant on refresh");
            }
            log.warn(
                    "Google /token refresh failed: status={}, body={}",
                    e.getStatusCode(),
                    responseBody);
            throw new UpstreamUnavailableException(
                    "Google /token refresh returned " + e.getStatusCode(), e);
        } catch (GoogleAuthRevokedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google /token refresh failed: {}", e.getMessage());
            throw new UpstreamUnavailableException("Google /token unreachable", e);
        }
    }
}
