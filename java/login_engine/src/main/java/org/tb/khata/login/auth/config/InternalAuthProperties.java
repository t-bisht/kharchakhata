package org.tb.khata.login.auth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Config for the service-to-service auth boundary on {@code /internal/**}.
 *
 * <p>{@code svcToken} is a shared secret every internal caller (ingestion, user_engine, etc.)
 * echoes in the {@code X-Internal-Auth} header. Manual rotation only for Day 1 (Open Q #9). Bound
 * to {@code app.internal.*}.
 */
@Validated
@ConfigurationProperties(prefix = "app.internal")
public record InternalAuthProperties(@NotBlank String svcToken) {}
