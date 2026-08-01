package org.tb.khata.login.auth.exception;

/**
 * Request body {@code user_id} does not match the {@code sub} of the forwarded JWT. Guards
 * against a compromised service fetching tokens for arbitrary users.
 */
public class UserContextMismatchException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UserContextMismatchException(String message) {
        super(message);
    }
}
