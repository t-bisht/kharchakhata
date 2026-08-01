package org.tb.khata.login.security.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tb.khata.login.security.SessionJwtVerifier;
import org.tb.khata.login.auth.config.InternalAuthProperties;
import org.tb.khata.login.auth.exception.InternalAuthInvalidException;
import org.tb.khata.login.auth.exception.InternalAuthMissingException;
import org.tb.khata.login.auth.exception.UserContextRequiredException;

/**
 * Filter for the {@code /internal/**} boundary — checks two authentication layers before the
 * request reaches the controller:
 *
 * <ol>
 *   <li>Service identity: {@code X-Internal-Auth} header must equal the shared secret in
 *       {@link InternalAuthProperties#svcToken()} (constant-time compare).
 *   <li>User context: forwarded {@code Authorization: Bearer <jwt>} is verified locally against
 *       our RSA public key; the {@code sub} claim is attached to the request as an attribute
 *       for controllers to consume (see {@link #ATTR_USER_SUB}).
 * </ol>
 *
 * <p>Body-level {@code user_id == sub} matching is enforced by controllers, not this filter — the
 * body is easier to read cleanly there.
 */
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalAuthFilter.class);

    /** Header name — a legitimate internal caller must send this. */
    public static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    /** Request attribute where controllers pick up the verified user sub. */
    public static final String ATTR_USER_SUB = "kk.userSub";

    private final InternalAuthProperties props;
    private final SessionJwtVerifier jwtVerifier;

    public InternalAuthFilter(InternalAuthProperties props, SessionJwtVerifier jwtVerifier) {
        this.props = props;
        this.jwtVerifier = jwtVerifier;
    }

    /** Only run for /internal/** — skip everything else. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String svcHeader = request.getHeader(INTERNAL_AUTH_HEADER);
        if (svcHeader == null || svcHeader.isBlank()) {
            throw new InternalAuthMissingException("X-Internal-Auth header missing");
        }
        if (!MessageDigest.isEqual(
                svcHeader.getBytes(StandardCharsets.UTF_8),
                props.svcToken().getBytes(StandardCharsets.UTF_8))) {
            throw new InternalAuthInvalidException("X-Internal-Auth does not match");
        }

        String authz = request.getHeader("Authorization");
        if (authz == null || !authz.startsWith("Bearer ")) {
            throw new UserContextRequiredException("Authorization Bearer JWT missing");
        }
        String jwt = authz.substring("Bearer ".length()).trim();

        Claims claims;
        try {
            claims = jwtVerifier.verify(jwt);
        } catch (JwtException e) {
            log.warn("Rejected /internal/** request — JWT verify failed: {}", e.getMessage());
            throw new UserContextRequiredException("JWT invalid: " + e.getMessage());
        }

        request.setAttribute(ATTR_USER_SUB, claims.getSubject());
        chain.doFilter(request, response);
    }
}
