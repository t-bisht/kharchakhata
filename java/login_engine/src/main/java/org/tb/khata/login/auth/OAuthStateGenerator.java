package org.tb.khata.login.auth;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class OAuthStateGenerator {

    static final int STATE_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public String generate() {
        byte[] buf = new byte[STATE_BYTES];
        random.nextBytes(buf);
        return encoder.encodeToString(buf);
    }
}
