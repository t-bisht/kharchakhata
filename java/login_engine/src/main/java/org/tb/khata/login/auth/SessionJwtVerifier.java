package org.tb.khata.login.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

/**
 * Verifies session JWTs issued by {@link SessionJwtIssuer} using the local {@link
 * RsaKeyProvider} public key.
 *
 * <p>Auth_engine uses this on its own {@code /internal/**} endpoints to extract the user context
 * from the forwarded {@code Authorization: Bearer <jwt>}. Sub-engines will use a copy of this
 * pattern via a shared {@code jwt-verifier} library that pulls the public key from JWKS instead
 * of directly from RsaKeyProvider (spec §4.5 / Open Q #8).
 *
 * <p>{@code exp} check is enforced automatically by JJWT — expired tokens throw {@link
 * io.jsonwebtoken.ExpiredJwtException}. Signature failures throw {@link
 * io.jsonwebtoken.security.SignatureException}. Both bubble up as {@link JwtException}.
 */
@Component
public class SessionJwtVerifier {

    private final RsaKeyProvider keys;

    public SessionJwtVerifier(RsaKeyProvider keys) {
        this.keys = keys;
    }

    /**
     * Parse + verify a JWT; return the payload claims.
     *
     * @throws JwtException if signature is bad, token expired, or token malformed
     */
    public Claims verify(String jwt) {
        Jws<Claims> parsed =
                Jwts.parser()
                        .verifyWith(keys.publicKey())
                        .build()
                        .parseSignedClaims(jwt);
        return parsed.getPayload();
    }
}
