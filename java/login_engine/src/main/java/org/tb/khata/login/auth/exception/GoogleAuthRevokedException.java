package org.tb.khata.login.auth.exception;

/**
 * Google returned {@code invalid_grant} on refresh — user revoked our access from their Google
 * settings, or Google security invalidated the grant. Non-recoverable without new consent.
 * Spec §4.8.
 */
public class GoogleAuthRevokedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public GoogleAuthRevokedException(String message) {
        super(message);
    }
}
