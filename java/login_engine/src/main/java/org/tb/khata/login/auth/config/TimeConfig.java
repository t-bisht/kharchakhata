package org.tb.khata.login.auth.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link Clock} bean so components that need "now" (e.g. {@link
 * org.tb.khata.login.auth.SessionJwtIssuer} for {@code iat} / {@code exp} claims) can inject it
 * instead of calling {@link java.time.Instant#now()} directly.
 *
 * <p>Production uses UTC; tests override with {@link Clock#fixed} for deterministic time.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock systemUtcClock() {
        return Clock.systemUTC();
    }
}
