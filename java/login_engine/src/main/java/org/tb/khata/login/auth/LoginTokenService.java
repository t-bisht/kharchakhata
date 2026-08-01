package org.tb.khata.login.auth;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.khata.login.auth.dto.GoogleTokenResponse;
import org.tb.khata.login.auth.exception.TokenPersistenceException;
import org.tb.khata.login.auth.persistence.LoginToken;
import org.tb.khata.login.auth.persistence.LoginTokenRepository;

/**
 * Owns writes to {@code login_tokens}. Called at the tail of a successful login (spec §4.2 step 5)
 * to UPSERT the Google-issued tokens keyed by user_id (= Google sub).
 *
 * <p>Encryption is transparent — handled by {@link
 * org.tb.khata.login.auth.persistence.EncryptedStringConverter} on the entity fields.
 */
@Service
public class LoginTokenService {

    private static final Logger log = LoggerFactory.getLogger(LoginTokenService.class);

    private final LoginTokenRepository repository;
    private final Clock clock;

    public LoginTokenService(LoginTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * UPSERT the tokens for a user. Existing row is updated in place; new row inserted otherwise.
     * Google occasionally rotates refresh_token — we always take whatever it returned; when it's
     * absent (typical on refresh) we keep the previous value.
     *
     * @throws TokenPersistenceException on any DB failure — propagates to {@code
     *     /login?err=db_error}
     */
    @Transactional
    public void upsertFromGoogle(String userId, GoogleTokenResponse tokens) {
        try {
            Instant accessExpiry =
                    tokens.expiresIn() == null
                            ? clock.instant()
                            : clock.instant().plusSeconds(tokens.expiresIn());

            LoginToken existing = repository.findById(userId).orElse(null);
            if (existing == null) {
                LoginToken created =
                        new LoginToken(
                                userId,
                                tokens.accessToken(),
                                accessExpiry,
                                tokens.refreshToken(),
                                tokens.scope());
                repository.save(created);
                log.info("Inserted login_tokens row for user_id={}", userId);
            } else {
                existing.updateAccessToken(tokens.accessToken(), accessExpiry);
                if (tokens.refreshToken() != null) {
                    // Only overwrite when Google actually gave us a new one — otherwise keep old.
                    existing.updateRefreshToken(tokens.refreshToken());
                }
                repository.save(existing);
                log.info("Updated login_tokens row for user_id={}", userId);
            }
        } catch (RuntimeException e) {
            throw new TokenPersistenceException("Failed to upsert login_tokens", e);
        }
    }
}
