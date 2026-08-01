package org.tb.khata.login.auth.gcp.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.khata.login.auth.config.RedirectAllowlistProperties;

class RedirectionResolverTest {

    private RedirectionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RedirectionResolver();
        ReflectionTestUtils.setField(
                resolver,
                "postLogin",
                new RedirectAllowlistProperties(
                        "/dashboard", List.of("/dashboard", "/expenses", "/settings")));
    }

    @Test
    void nullFallsBackToDefault() {
        assertThat(resolver.resolveRedirect(null)).isEqualTo("/dashboard");
    }

    @Test
    void blankFallsBackToDefault() {
        assertThat(resolver.resolveRedirect("   ")).isEqualTo("/dashboard");
    }

    @Test
    void absoluteUrlRejected() {
        assertThat(resolver.resolveRedirect("https://evil.com/x")).isEqualTo("/dashboard");
    }

    @Test
    void schemaRelativeUrlRejected() {
        // Protocol-relative like //evil.com would inherit page scheme — treat as absolute.
        assertThat(resolver.resolveRedirect("//evil.com/x")).isEqualTo("/dashboard");
    }

    @Test
    void nonRelativePathRejected() {
        assertThat(resolver.resolveRedirect("relative-no-slash")).isEqualTo("/dashboard");
    }

    @Test
    void allowlistedPathPassesThrough() {
        assertThat(resolver.resolveRedirect("/expenses")).isEqualTo("/expenses");
    }

    @Test
    void nonAllowlistedPathFallsBackToDefault() {
        assertThat(resolver.resolveRedirect("/some-random-path")).isEqualTo("/dashboard");
    }
}
