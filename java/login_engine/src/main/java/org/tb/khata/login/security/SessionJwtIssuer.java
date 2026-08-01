package org.tb.khata.login.security;

import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Component;
import org.tb.khata.login.security.config.JwtProperties;
import org.tb.khata.login.auth.gcp.dto.IdentityClaims;

/**
 * Signs "thick" session JWTs using RS256 with the key held by {@link RsaKeyProvider}.
 *
 * <p>Thick JWT: the SPA reads display fields ({@code email}, {@code name}, {@code picture})
 * directly from the token, avoiding a round-trip to {@code user_engine} on every page load. See
 * spec §7.1 for the exact claim set.
 *
 * <p>Every JWT carries the active {@code kid} in the header so sub-engines can pick the correct
 * public key out of the JWKS when signature-verifying — this is what makes zero-downtime key
 * rotation possible (spec §4.11).
 */
@Component
public class SessionJwtIssuer {

    private final RsaKeyProvider keys;
    private final JwtProperties props;
    private final Clock clock;

    public SessionJwtIssuer(RsaKeyProvider keys, JwtProperties props, Clock clock) {
        this.keys = keys;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Mints a session JWT for the given identity + granted scopes.
     *
     * @param identity Google-derived identity fields ({@code sub}, {@code email}, ...)
     * @param scopes Google scopes granted at consent time, e.g. {@code [gmail.readonly]}
     * @return compact serialized JWT (three base64url parts joined by {@code .})
     */
    public String issue(IdentityClaims identity, List<String> scopes) {
        Instant now = clock.instant();
        Instant exp = now.plus(Duration.ofHours(props.expirationHours()));

        return Jwts.builder()
                .header()
                .keyId(keys.activeKid())
                .and()
                .issuer(props.issuer())
                .subject(identity.sub())
                .claim("email", identity.email())
                .claim("name", identity.name())
                .claim("picture", identity.picture())
                .claim("scope", scopes)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(keys.privateKey(), Jwts.SIG.RS256)
                .compact();
    }
}
