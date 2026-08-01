package org.tb.khata.login.security.security;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.tb.khata.login.security.config.TokenCipherProperties;

/**
 * AES-256-GCM encryption for Google tokens at rest (spec §9.1 Open Q #5).
 *
 * <p>Wire format: {@code enc_v1:<base64url-iv>:<base64url-ciphertext-with-tag>}. The version
 * prefix ({@code v1}) lets future rotations coexist by decrypting old rows under the old key and
 * re-encrypting under the new one. GCM provides authenticated encryption — any tamper with the
 * ciphertext throws at decrypt.
 *
 * <p>IV is 96 bits (NIST-recommended for GCM), freshly random per encrypt call. Never reuse an
 * (IV, key) pair — that would break GCM entirely.
 */
@Component
public class TokenCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PREFIX = "enc_v1:";
    private static final int IV_LENGTH_BYTES = 12; // 96 bits — GCM recommended
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final TokenCipherProperties props;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    private SecretKeySpec keySpec;

    public TokenCipher(TokenCipherProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        byte[] keyBytes = Base64.getDecoder().decode(props.key());
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "APP_TOKEN_ENC_KEY must decode to 32 bytes (AES-256), got "
                            + keyBytes.length);
        }
        this.keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /** Encrypts a UTF-8 string; returns {@code enc_v1:<iv>:<ct>} or {@code null} for null input. */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        byte[] iv = new byte[IV_LENGTH_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + encoder.encodeToString(iv) + ":" + encoder.encodeToString(ct);
        } catch (Exception e) {
            throw new IllegalStateException("Encrypt failed", e);
        }
    }

    /** Decrypts a value written by {@link #encrypt(String)}. Returns {@code null} for null input. */
    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        if (!encrypted.startsWith(PREFIX)) {
            throw new IllegalStateException("Unrecognised encryption prefix on stored token");
        }
        String[] parts = encrypted.substring(PREFIX.length()).split(":");
        if (parts.length != 2) {
            throw new IllegalStateException("Malformed encrypted token — expected iv:ct");
        }
        byte[] iv = decoder.decode(parts[0]);
        byte[] ct = decoder.decode(parts[1]);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // GCM auth failure lands here as well — any tamper throws.
            throw new IllegalStateException("Decrypt failed (auth check or corruption)", e);
        }
    }
}
