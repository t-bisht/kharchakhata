package org.tb.khata.login.auth.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.tb.khata.login.auth.dto.IdentityClaims;
import org.tb.khata.login.auth.exception.EmailUnverifiedException;
import org.tb.khata.login.auth.exception.IdTokenMalformedException;

class IdTokenClaimsReaderTest {

    private final IdTokenClaimsReader reader = new IdTokenClaimsReader(new ObjectMapper());

    @Test
    void extractsClaimsFromVerifiedIdToken() {
        String idToken =
                buildIdToken(
                        "{\"sub\":\"sub-123\",\"email\":\"tb@example.com\","
                                + "\"email_verified\":true,\"name\":\"TB\","
                                + "\"picture\":\"https://pic/tb\"}");

        IdentityClaims claims = reader.readClaims(idToken);

        assertThat(claims).isEqualTo(new IdentityClaims("sub-123", "tb@example.com", "TB", "https://pic/tb"));
    }

    @Test
    void rejectsWhenEmailVerifiedFalse() {
        String idToken =
                buildIdToken(
                        "{\"sub\":\"s\",\"email\":\"a@b\",\"email_verified\":false,\"name\":\"n\"}");
        assertThatThrownBy(() -> reader.readClaims(idToken))
                .isInstanceOf(EmailUnverifiedException.class);
    }

    @Test
    void rejectsWhenEmailVerifiedMissing() {
        String idToken = buildIdToken("{\"sub\":\"s\",\"email\":\"a@b\",\"name\":\"n\"}");
        assertThatThrownBy(() -> reader.readClaims(idToken))
                .isInstanceOf(EmailUnverifiedException.class);
    }

    @Test
    void rejectsMalformedTokenShape() {
        assertThatThrownBy(() -> reader.readClaims("only-two.parts"))
                .isInstanceOf(IdTokenMalformedException.class)
                .hasMessageContaining("3 dot-separated parts");
    }

    @Test
    void rejectsMissingSub() {
        String idToken = buildIdToken("{\"email\":\"a@b\",\"email_verified\":true}");
        assertThatThrownBy(() -> reader.readClaims(idToken))
                .isInstanceOf(IdTokenMalformedException.class)
                .hasMessageContaining("sub");
    }

    /** Wraps a JSON payload in a fake 3-part JWT-shaped string ({@code header.payload.sig}). */
    private static String buildIdToken(String payloadJson) {
        String header = base64Url("{\"alg\":\"RS256\",\"kid\":\"g1\"}");
        String payload = base64Url(payloadJson);
        return header + "." + payload + ".fake-sig-not-verified";
    }

    private static String base64Url(String s) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
