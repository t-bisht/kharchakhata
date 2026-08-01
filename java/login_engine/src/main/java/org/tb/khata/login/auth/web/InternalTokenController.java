package org.tb.khata.login.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tb.khata.login.auth.GoogleTokenRefresher;
import org.tb.khata.login.auth.exception.LoginTokenNotFoundException;
import org.tb.khata.login.auth.exception.UserContextMismatchException;
import org.tb.khata.login.auth.persistence.LoginToken;
import org.tb.khata.login.auth.persistence.LoginTokenRepository;
import org.tb.khata.login.auth.security.InternalAuthFilter;

/**
 * Service-to-service API for Google access tokens. Spec §4.6 / §4.7 / §4.8 / §4.9.
 *
 * <p>Guarded by {@link InternalAuthFilter} — arrives here only if X-Internal-Auth matched and a
 * user JWT verified. The forwarded user's {@code sub} is on the request attribute.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>{@code POST /internal/tokens/access} — return a valid access_token, refreshing if needed
 *   <li>{@code POST /internal/tokens/invalidate} — null out access_token to force refresh
 * </ul>
 */
@RestController
@RequestMapping("/internal/tokens")
public class InternalTokenController {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenController.class);

    /** Refresh eagerly when access_token is within this window of expiry. Spec §4.7. */
    private static final long REFRESH_LEAD_TIME_SECONDS = 60L;

    private final LoginTokenRepository repository;
    private final GoogleTokenRefresher refresher;
    private final Clock clock;

    public InternalTokenController(
            LoginTokenRepository repository, GoogleTokenRefresher refresher, Clock clock) {
        this.repository = repository;
        this.refresher = refresher;
        this.clock = clock;
    }

    /**
     * §4.6 fast path + §4.7 refresh path. Returns a valid access_token — refreshes via Google
     * transparently if the stored one is expired or within {@link #REFRESH_LEAD_TIME_SECONDS}.
     */
    @PostMapping("/access")
    public ResponseEntity<AccessTokenResponse> access(
            @RequestBody TokenAccessRequest body, HttpServletRequest request) {

        requireBodyMatchesSub(body.userId(), request);
        LoginToken token =
                repository
                        .findById(body.userId())
                        .orElseThrow(() -> new LoginTokenNotFoundException(body.userId()));

        boolean needsRefresh =
                token.accessToken() == null
                        || token.accessTokenExpiry() == null
                        || token.accessTokenExpiry()
                                .isBefore(clock.instant().plusSeconds(REFRESH_LEAD_TIME_SECONDS));

        if (needsRefresh) {
            token = refresher.refresh(body.userId());
        }
        return ResponseEntity.ok(
                new AccessTokenResponse(token.accessToken(), token.accessTokenExpiry().toString()));
    }

    /**
     * §4.9 — nulls out the stored access_token so the next {@code /access} call takes the refresh
     * path. Called by sub-engines when Google unexpectedly rejects an access_token before its
     * recorded expiry.
     */
    @PostMapping("/invalidate")
    @Transactional
    public ResponseEntity<Void> invalidate(
            @RequestBody TokenAccessRequest body, HttpServletRequest request) {

        requireBodyMatchesSub(body.userId(), request);
        LoginToken token =
                repository
                        .findById(body.userId())
                        .orElseThrow(() -> new LoginTokenNotFoundException(body.userId()));
        token.invalidateAccessToken();
        repository.save(token);
        log.info("Invalidated access_token for user_id={}", body.userId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** Body user_id MUST equal the sub claim in the forwarded JWT — prevents cross-user fetch. */
    private static void requireBodyMatchesSub(String bodyUserId, HttpServletRequest request) {
        String sub = (String) request.getAttribute(InternalAuthFilter.ATTR_USER_SUB);
        if (!bodyUserId.equals(sub)) {
            throw new UserContextMismatchException(
                    "body.user_id (%s) does not match JWT.sub (%s)".formatted(bodyUserId, sub));
        }
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────

    public record TokenAccessRequest(@NotBlank String userId) {}

    public record AccessTokenResponse(String accessToken, String expiresAt) {}
}
