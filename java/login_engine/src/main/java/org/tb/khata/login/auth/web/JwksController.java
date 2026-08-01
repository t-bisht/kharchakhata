package org.tb.khata.login.auth.web;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tb.khata.login.auth.RsaKeyProvider;

/**
 * Publishes {@code auth_engine}'s public signing key(s) as a JWK Set (RFC 7517) so sub-engines
 * can verify session JWTs purely locally without ever calling back to us. Spec §4.5.
 *
 * <p>Response is cacheable for 1 hour — safe because keys change only on rotation, and sub-engines
 * additionally re-fetch on {@code kid} cache-miss (spec §4.5). The endpoint is deliberately
 * unauthenticated: public keys are, by design, public.
 *
 * <p>Currently emits a single key (the active one). Rotation (spec §4.11) will extend to a list
 * — both old + new appear during the overlap window.
 */
@RestController
public class JwksController {

    private final RsaKeyProvider keys;

    public JwksController(RsaKeyProvider keys) {
        this.keys = keys;
    }

    @GetMapping(path = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> jwks() {
        Map<String, Object> jwk = buildRsaJwk(keys.publicKey(), keys.activeKid());
        Map<String, Object> body = Map.of("keys", List.of(jwk));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic())
                .body(body);
    }

    /**
     * Build a JWK for an RSA public key per RFC 7517 §4 / RFC 7518 §6.3. Fields:
     *
     * <ul>
     *   <li>{@code kty} — key type; always "RSA"
     *   <li>{@code use} — key usage; "sig" (signature)
     *   <li>{@code alg} — algorithm hint; "RS256"
     *   <li>{@code kid} — key id; matches the one stamped on the JWT header
     *   <li>{@code n} — RSA modulus, unsigned big-endian, base64url unpadded
     *   <li>{@code e} — RSA public exponent, unsigned big-endian, base64url unpadded (usually AQAB = 65537)
     * </ul>
     */
    private static Map<String, Object> buildRsaJwk(RSAPublicKey key, String kid) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", kid,
                "n", enc.encodeToString(unsignedBigEndian(key.getModulus())),
                "e", enc.encodeToString(unsignedBigEndian(key.getPublicExponent())));
    }

    /**
     * BigInteger.toByteArray() returns two's-complement — for positive numbers with a high bit set
     * it prepends a 0x00 sign byte. JWK requires unsigned big-endian, so strip it.
     */
    private static byte[] unsignedBigEndian(java.math.BigInteger n) {
        byte[] bytes = n.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] stripped = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, stripped, 0, stripped.length);
            return stripped;
        }
        return bytes;
    }
}
