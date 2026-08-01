package org.tb.khata.login.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tb.khata.login.security.config.JwtProperties;

class RsaKeyProviderTest {

    @Test
    void loadsPemFromDiskAndExposesKeypair(@TempDir Path tmp) throws Exception {
        Path pemPath = writeTempPem(tmp);
        RsaKeyProvider provider = new RsaKeyProvider(props(pemPath));

        provider.load();

        assertThat(provider.privateKey()).isNotNull();
        assertThat(provider.publicKey()).isNotNull();
        // Public key modulus must match private key modulus — proves the derivation.
        assertThat(provider.publicKey().getModulus())
                .isEqualTo(provider.privateKey().getModulus());
        assertThat(provider.activeKid()).isEqualTo("v1");
    }

    @Test
    void failsFastWhenPemMissing(@TempDir Path tmp) {
        RsaKeyProvider provider = new RsaKeyProvider(props(tmp.resolve("does-not-exist.pem")));
        assertThatThrownBy(provider::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to read JWT private key");
    }

    @Test
    void failsFastWhenPemMalformed(@TempDir Path tmp) throws IOException {
        Path pemPath = tmp.resolve("bad.pem");
        Files.writeString(
                pemPath,
                "-----BEGIN PRIVATE KEY-----\nnot-real-base64!!!\n-----END PRIVATE KEY-----\n");
        RsaKeyProvider provider = new RsaKeyProvider(props(pemPath));
        assertThatThrownBy(provider::load).isInstanceOf(IllegalArgumentException.class);
    }

    private static JwtProperties props(Path pemPath) {
        return new JwtProperties(pemPath.toString(), "v1", "auth_engine", 24L);
    }

    /** Generate an RSA keypair on the fly and write the private key in PEM format. */
    private static Path writeTempPem(Path tmp) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String base64 =
                Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(kp.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
        Path path = tmp.resolve("jwt-private.pem");
        Files.writeString(path, pem);
        return path;
    }
}
