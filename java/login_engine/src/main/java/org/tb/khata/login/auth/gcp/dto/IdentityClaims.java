package org.tb.khata.login.auth.gcp.dto;

import org.tb.khata.login.auth.gcp.IdTokenClaimsReader;
import org.tb.khata.login.security.SessionJwtIssuer;

/**
 * Identity fields extracted from a Google {@code id_token} payload.
 *
 * <p>Constructed by {@link IdTokenClaimsReader}. Consumed by {@link
 * SessionJwtIssuer} when minting session JWTs, and (later) by the {@code
 * user_engine} upsert call.
 *
 * <p>Values are trusted because the {@code id_token} arrives back-channel from Google over TLS —
 * see spec Open Q #15 on skipping signature verification.
 */
public record IdentityClaims(String sub, String email, String name, String picture) {}
