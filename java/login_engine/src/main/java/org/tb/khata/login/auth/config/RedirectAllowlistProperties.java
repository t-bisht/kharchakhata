package org.tb.khata.login.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.oauth.post-login")
public record RedirectAllowlistProperties(
        @NotBlank String defaultPath, @NotEmpty List<@NotBlank String> allowedPaths) {

    public boolean isAllowed(String path) {
        return path != null && allowedPaths.contains(path);
    }
}
