package org.tb.khata.login.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.tb.khata.login.auth.security.InternalAuthFilter;

/**
 * Spring Security wiring.
 *
 * <p>Two boundaries:
 *
 * <ul>
 *   <li>{@code /api/auth/**}, {@code /.well-known/**}, {@code /api/actuator/**} — public
 *   <li>{@code /internal/**} — {@link InternalAuthFilter} enforces X-Internal-Auth + Bearer JWT
 *       BEFORE the request reaches Spring's authorize step
 * </ul>
 *
 * <p>CSRF is disabled at the framework level — session cookies use SameSite=Lax + the OAuth flow
 * uses per-request state cookies; logout uses double-submit CSRF handled in the controller.
 * Sessions are stateless — JWTs live entirely in cookies.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, InternalAuthFilter internalAuthFilter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/api/auth/**",
                                                "/.well-known/**",
                                                "/api/actuator/**",
                                                "/internal/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                // Wire in the internal-auth filter BEFORE the standard security filter chain runs
                // its authorize step — anything past this filter has already been authenticated
                // at the service + user layer.
                .addFilterBefore(
                        internalAuthFilter,
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        return http.build();
    }
}
