package org.tb.khata.login.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persistent record of a user's Google-issued tokens. One row per {@code user_id} (Google {@code
 * sub}). Access and refresh tokens are stored encrypted at rest — see {@link EncryptedStringConverter}.
 *
 * <p>Written by {@link org.tb.khata.login.auth.LoginTokenService} at the tail of a successful
 * login (spec §4.2 step 5) and updated by {@code GoogleTokenRefresher} on refresh (§4.7).
 */
@Entity
@Table(name = "login_tokens")
public class LoginToken {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    /** Encrypted at rest via {@link EncryptedStringConverter}. Nullable when refresh needed. */
    @Column(name = "access_token")
    @Convert(converter = EncryptedStringConverter.class)
    private String accessToken;

    @Column(name = "access_token_expiry")
    private Instant accessTokenExpiry;

    /** Encrypted at rest. Long-lived; only mutated when Google rotates it (rare). */
    @Column(name = "refresh_token")
    @Convert(converter = EncryptedStringConverter.class)
    private String refreshToken;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LoginToken() {} // JPA

    public LoginToken(
            String userId,
            String accessToken,
            Instant accessTokenExpiry,
            String refreshToken,
            String scope) {
        this.userId = userId;
        this.accessToken = accessToken;
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshToken = refreshToken;
        this.scope = scope;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String userId() { return userId; }
    public String accessToken() { return accessToken; }
    public Instant accessTokenExpiry() { return accessTokenExpiry; }
    public String refreshToken() { return refreshToken; }
    public String scope() { return scope; }

    /** Mutator used by refresh-path: replace access token + its expiry. */
    public void updateAccessToken(String newAccessToken, Instant newExpiry) {
        this.accessToken = newAccessToken;
        this.accessTokenExpiry = newExpiry;
    }

    /** Mutator used when Google rotates the refresh token on a refresh call. */
    public void updateRefreshToken(String newRefreshToken) {
        this.refreshToken = newRefreshToken;
    }

    /** Force next /access call down the refresh path (spec §4.9). */
    public void invalidateAccessToken() {
        this.accessToken = null;
        this.accessTokenExpiry = null;
    }

    /** Wipe both tokens after a Google invalid_grant (spec §4.8). */
    public void wipeTokens() {
        this.accessToken = null;
        this.accessTokenExpiry = null;
        this.refreshToken = null;
    }
}
