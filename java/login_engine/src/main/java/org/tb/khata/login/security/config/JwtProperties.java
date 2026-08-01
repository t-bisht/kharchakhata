package org.tb.khata.login.security.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.tb.khata.login.security.RsaKeyProvider;

/**
 * Configuration for session-JWT signing.
 *
 * <p>All fields are required — the app fails to boot if any is missing or blank. The private key
 * PEM is loaded from disk at startup by {@link RsaKeyProvider}. The active
 * {@code kid} is stamped into the header of every JWT this service mints; sub-engines will use it
 * to pick the right public key out of the JWKS when key rotation happens.
 *
 * <p>Bound to the {@code app.jwt.*} tree in {@code application.yml}. Env vars {@code
 * APP_JWT_PRIVATE_KEY_PATH}, {@code APP_JWT_ACTIVE_KID}, {@code APP_JWT_ISSUER}, {@code
 * APP_JWT_EXPIRATION_HOURS} override defaults.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        /** Absolute filesystem path to the PEM-encoded RSA private key. */
        @NotBlank String privateKeyPath,

        /** Key ID stamped into the JWT header; also the {@code kid} published in JWKS. */
        @NotBlank String activeKid,

        /** Value used for the {@code iss} claim. Sub-engines may pin this at verify time. */
        @NotBlank String issuer,

        /** Session JWT lifetime, in hours. Spec default = 24. */
        @Min(1) long expirationHours) {}
