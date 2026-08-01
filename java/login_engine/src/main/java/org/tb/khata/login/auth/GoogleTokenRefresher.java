package org.tb.khata.login.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.khata.login.auth.dto.GoogleTokenResponse;
import org.tb.khata.login.auth.exception.GoogleAuthRevokedException;
import org.tb.khata.login.auth.exception.LoginTokenNotFoundException;
import org.tb.khata.login.auth.gcp.GoogleOAuthClient;
import org.tb.khata.login.auth.persistence.LoginToken;
import org.tb.khata.login.auth.persistence.LoginTokenRepository;

/**
 * Refreshes a user's Google access_token on demand. Spec §4.7.
 *
 * <p>Concurrency: uses a per-user in-process {@link ReentrantLock}. Parallel refresh requests for
 * the same user coalesce onto a single Google roundtrip — first thread refreshes, subsequent
 * threads wait on the lock and read the just-written row. Prevents "thundering herd" on Google's
 * /token endpoint.
 *
 * <p>On Google returning {@code invalid_grant}, both stored tokens are wiped and {@link
 * GoogleAuthRevokedException} is thrown — spec §4.8.
 */
@Service
public class GoogleTokenRefresher {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenRefresher.class);

    private final LoginTokenRepository repository;
    private final GoogleOAuthClient googleClient;
    private final Clock clock;

    /** Per-user in-process lock. Loaded lazily. Not persistent — one-JVM scope. */
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public GoogleTokenRefresher(
            LoginTokenRepository repository, GoogleOAuthClient googleClient, Clock clock) {
        this.repository = repository;
        this.googleClient = googleClient;
        this.clock = clock;
    }

    /**
     * Perform a Google refresh call for the given user and persist the result. If a parallel
     * refresh for the same user is already in flight, block until it completes and then use its
     * already-updated row (short-circuiting the second Google call).
     *
     * @return the refreshed {@link LoginToken} row
     */
    @Transactional
    public LoginToken refresh(String userId) {
        ReentrantLock lock = locks.computeIfAbsent(userId, k -> new ReentrantLock());
        lock.lock();
        try {
            LoginToken existing =
                    repository
                            .findById(userId)
                            .orElseThrow(() -> new LoginTokenNotFoundException(userId));

            // Second-check: another thread may have refreshed while we waited on the lock.
            if (existing.accessToken() != null
                    && existing.accessTokenExpiry() != null
                    && existing.accessTokenExpiry().isAfter(clock.instant().plusSeconds(60))) {
                return existing;
            }

            if (existing.refreshToken() == null) {
                throw new GoogleAuthRevokedException(
                        "No refresh_token stored — user must re-consent");
            }

            GoogleTokenResponse fresh;
            try {
                fresh = googleClient.refresh(existing.refreshToken());
            } catch (GoogleAuthRevokedException e) {
                // Spec §4.8 — wipe both tokens; user must re-consent.
                existing.wipeTokens();
                repository.save(existing);
                throw e;
            }

            Instant newExpiry =
                    fresh.expiresIn() == null
                            ? clock.instant()
                            : clock.instant().plusSeconds(fresh.expiresIn());
            existing.updateAccessToken(fresh.accessToken(), newExpiry);
            if (fresh.refreshToken() != null) {
                // Google rotated it — persist the new value.
                existing.updateRefreshToken(fresh.refreshToken());
            }
            repository.save(existing);
            log.info("Refreshed access_token for user_id={}, new expiry={}", userId, newExpiry);
            return existing;
        } finally {
            lock.unlock();
        }
    }
}
