package org.tb.khata.login.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OAuthStateGeneratorTest {

    private static final Pattern BASE64URL_UNPADDED = Pattern.compile("^[A-Za-z0-9_-]+$");
    // 32 bytes → base64url unpadded = ceil(32 * 4 / 3) = 43 chars.
    private static final int EXPECTED_LENGTH = 43;

    private final OAuthStateGenerator generator = new OAuthStateGenerator();

    @Test
    void generatesBase64UrlOfExpectedLength() {
        String s = generator.generate();
        assertThat(s).hasSize(EXPECTED_LENGTH);
        assertThat(BASE64URL_UNPADDED.matcher(s).matches()).isTrue();
    }

    @Test
    void generatesDistinctValuesAcrossManyCalls() {
        int n = 1_000;
        Set<String> seen = new HashSet<>(n);
        for (int i = 0; i < n; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen).hasSize(n);
    }
}
