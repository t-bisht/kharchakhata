package org.tb.khata.login.auth;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.tb.khata.login.auth.config.JwtProperties;

/**
 * Loads the RSA keypair used for session-JWT signing and JWKS publication.
 *
 * <p>On boot, reads the PEM file at {@link JwtProperties#privateKeyPath()}, parses out the
 * PKCS#8-encoded private key, derives the matching public key from the modulus + public exponent,
 * and caches both. All JWT signing (see {@link SessionJwtIssuer}) uses the cached private key; the
 * JWKS controller (later) will serve the public key.
 *
 * <p>Fail-fast contract: if the file is missing, unreadable, or malformed, {@link #load()} throws
 * from {@code @PostConstruct}, aborting Spring startup with a clear message. That is intentional —
 * the service cannot function without a signing key, and refusing to start is safer than silently
 * booting and failing on the first login.
 *
 * <p>Currently supports a single active key. Rotation (spec §4.11) will extend this to a keyset.
 */
@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);

    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";

    private final JwtProperties props;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    public RsaKeyProvider(JwtProperties props) {
        this.props = props;
    }

    /**
     * Reads and parses the PEM file. Invoked automatically by Spring after bean construction.
     * Aborts application startup with {@link IllegalStateException} on any failure.
     */
    @PostConstruct
    void load() {
        Path path = Path.of(props.privateKeyPath());
        String pem;
        try {
            pem = Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read JWT private key from " + path.toAbsolutePath(), e);
        }

        byte[] der = decodePem(pem);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            this.privateKey = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
            // Derive the public key from the private key's modulus + public exponent — no separate
            // .pub file needed. Only works for RSA keys; would need `openssl rsa -pubout` for EC.
            this.publicKey =
                    (RSAPublicKey)
                            kf.generatePublic(
                                    new RSAPublicKeySpec(
                                            this.privateKey.getModulus(),
                                            java.math.BigInteger.valueOf(65537)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(
                    "Failed to parse RSA private key from " + path.toAbsolutePath(), e);
        }

        log.info(
                "Loaded RSA private key from {} (kid={}, modulus bit length={})",
                path,
                props.activeKid(),
                privateKey.getModulus().bitLength());
    }

    /** Strips PEM header/footer + whitespace, returns raw DER bytes. */
    private static byte[] decodePem(String pem) {
        String stripped =
                pem.replace(PEM_HEADER, "")
                        .replace(PEM_FOOTER, "")
                        .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(stripped);
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    /** Active key id — stamped into JWT headers, published in JWKS. */
    public String activeKid() {
        return props.activeKid();
    }
}
