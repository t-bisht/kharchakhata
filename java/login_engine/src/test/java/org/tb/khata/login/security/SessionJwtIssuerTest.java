package org.tb.khata.login.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tb.khata.login.auth.gcp.dto.IdentityClaims;
import org.tb.khata.login.security.config.JwtProperties;

class SessionJwtIssuerTest {

    private RsaKeyProvider keyProvider;
    private JwtProperties props;
    private Clock fixedClock;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        Path pem = writeTempPem(tmp);
        this.props = new JwtProperties(pem.toString(), "v1", "auth_engine", 24L);
        this.keyProvider = new RsaKeyProvider(props);
        this.keyProvider.load();
        this.fixedClock = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void mintsRs256JwtWithSpecClaimsAndKid() {
        SessionJwtIssuer issuer = new SessionJwtIssuer(keyProvider, props, fixedClock);
        IdentityClaims identity =
                new IdentityClaims("sub-123", "tb@example.com", "TB", "https://pic/tb");

        String jwt = issuer.issue(identity, List.of("gmail.readonly"));

        Jws<Claims> parsed =
                Jwts.parser().verifyWith(keyProvider.publicKey()).build().parseSignedClaims(jwt);
        Claims claims = parsed.getPayload();

        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("RS256");
        assertThat(parsed.getHeader().getKeyId()).isEqualTo("v1");
        assertThat(claims.getIssuer()).isEqualTo("auth_engine");
        assertThat(claims.getSubject()).isEqualTo("sub-123");
        assertThat(claims.get("email")).isEqualTo("tb@example.com");
        assertThat(claims.get("name")).isEqualTo("TB");
        assertThat(claims.get("picture")).isEqualTo("https://pic/tb");
        assertThat(claims.get("scope", List.class)).containsExactly("gmail.readonly");
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(claims.getExpiration().toInstant())
                .isEqualTo(Instant.parse("2026-08-02T00:00:00Z"));
    }

    private static Path writeTempPem(Path tmp) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String b64 =
                Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(kp.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
        Path path = tmp.resolve("jwt-private.pem");
        Files.writeString(path, pem);
        return path;
    }
}
