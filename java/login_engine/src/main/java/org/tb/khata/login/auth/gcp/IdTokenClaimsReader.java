package org.tb.khata.login.auth.gcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.tb.khata.login.auth.gcp.dto.IdentityClaims;
import org.tb.khata.login.auth.exception.EmailUnverifiedException;
import org.tb.khata.login.auth.exception.IdTokenMalformedException;

/**
 * Extracts identity claims from a Google {@code id_token}.
 *
 * <p>An {@code id_token} is a signed JWT with three base64url-encoded parts joined by dots:
 * {@code <header>.<payload>.<signature>}. This reader decodes only the payload and enforces {@code
 * email_verified == true}.
 *
 * <p>Deliberately skips signature verification (spec Open Q #15, decided 2026-07-25): the token
 * arrived back-channel from Google over TLS during {@link GoogleOAuthClient#exchangeCode(String)}
 * — we didn't get it from the browser. Verifying its signature adds no security against a browser
 * MITM (the TLS channel already provides that) and would require fetching + caching Google's JWKS.
 *
 * <p>If we ever accept id_tokens from the browser (e.g. Google One-Tap on the SPA), reintroduce
 * RS256 verification against Google's JWKS.
 */
@Component
public class IdTokenClaimsReader {

    private final ObjectMapper mapper;

    public IdTokenClaimsReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Parses the id_token and returns the identity claims.
     *
     * @throws IdTokenMalformedException if the token isn't a well-formed three-part JWT or the
     *     payload isn't valid JSON
     * @throws EmailUnverifiedException if the {@code email_verified} claim is false or missing
     */
    public IdentityClaims readClaims(String idToken) {
        JsonNode payload = decodePayload(idToken);

        if (!payload.path("email_verified").asBoolean(false)) {
            throw new EmailUnverifiedException("id_token email_verified is false or missing");
        }

        return new IdentityClaims(
                requireText(payload, "sub"),
                requireText(payload, "email"),
                payload.path("name").asText(""),
                payload.path("picture").asText(""));
    }

    private JsonNode decodePayload(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new IdTokenMalformedException(
                    "id_token must have 3 dot-separated parts, got " + parts.length);
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            return mapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IdTokenMalformedException("id_token payload is not valid base64url", e);
        } catch (Exception e) {
            throw new IdTokenMalformedException("id_token payload is not valid JSON", e);
        }
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isEmpty()) {
            throw new IdTokenMalformedException("id_token payload missing required field: " + field);
        }
        return value.asText();
    }
}
