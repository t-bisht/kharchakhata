package org.tb.khata.login.security.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.tb.khata.login.security.security.TokenCipher;

/**
 * Config for {@link TokenCipher}.
 *
 * <p>Bound to {@code app.token-enc.*}. The key is a base64-encoded 32-byte secret (AES-256-GCM).
 * Generate with e.g. {@code openssl rand -base64 32}.
 */
@Validated
@ConfigurationProperties(prefix = "app.token-enc")
public record TokenCipherProperties(
        /** Base64-encoded AES key. 32 raw bytes → 44-char base64 including padding. */
        @NotBlank String key) {}
