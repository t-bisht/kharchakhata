package org.tb.khata.login.auth.gcp.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.khata.login.auth.config.RedirectAllowlistProperties;

@Component
public class RedirectionResolver {

    @Autowired
    RedirectAllowlistProperties postLogin;

    /**
     * Validates requested SPA return path — falls back to default on absolute URLs, schema-relative
     * URLs ({@code //evil.com/x}), or paths not in the allow-list.
     */
    public String resolveRedirect(String requested) {
        if (requested == null || requested.isBlank()) {
            return postLogin.defaultPath();
        }
        if (!requested.startsWith("/") || requested.startsWith("//")) {
            return postLogin.defaultPath();
        }
        return postLogin.isAllowed(requested) ? requested : postLogin.defaultPath();
    }
}
