package org.tb.khata.login.auth.gcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body from Google's {@code POST /token} endpoint.
 *
 * <p>Fields marked nullable are absent on refresh (only present on the initial code exchange). The
 * DTO is lenient — unknown fields are ignored to survive Google adding new response fields.
 *
 * @param accessToken short-lived Google access token (typically 1 hour)
 * @param refreshToken long-lived; only returned on initial exchange when {@code
 *     access_type=offline} + {@code prompt=consent}
 * @param idToken JWT-like blob containing user identity claims (unsigned by us, trusted TLS-side)
 * @param expiresIn access token lifetime in seconds
 * @param scope space-separated list of scopes granted
 * @param tokenType always {@code "Bearer"} in practice
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("id_token") String idToken,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("scope") String scope,
        @JsonProperty("token_type") String tokenType) {}
