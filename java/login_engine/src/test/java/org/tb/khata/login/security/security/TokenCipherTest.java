package org.tb.khata.login.security.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tb.khata.login.security.config.TokenCipherProperties;

class TokenCipherTest {

    private TokenCipher cipher;

    @BeforeEach
    void setUp() {
        // Deterministic 32-byte key for tests.
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;
        cipher = new TokenCipher(new TokenCipherProperties(Base64.getEncoder().encodeToString(key)));
        cipher.init();
    }

    @Test
    void roundTripsPlaintext() {
        String plaintext = "ya29.a0AS-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        String ct = cipher.encrypt(plaintext);
        assertThat(ct).startsWith("enc_v1:");
        assertThat(cipher.decrypt(ct)).isEqualTo(plaintext);
    }

    @Test
    void ivDiffersEveryCall() {
        String ct1 = cipher.encrypt("same");
        String ct2 = cipher.encrypt("same");
        assertThat(ct1).isNotEqualTo(ct2); // fresh IV each time → different ciphertexts
        assertThat(cipher.decrypt(ct1)).isEqualTo("same");
        assertThat(cipher.decrypt(ct2)).isEqualTo("same");
    }

    @Test
    void tamperingIsDetected() {
        String ct = cipher.encrypt("secret");
        // Flip a single character in the ciphertext portion.
        String tampered =
                ct.substring(0, ct.length() - 1)
                        + (ct.charAt(ct.length() - 1) == 'A' ? 'B' : 'A');
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullInputsPassThrough() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }
}
