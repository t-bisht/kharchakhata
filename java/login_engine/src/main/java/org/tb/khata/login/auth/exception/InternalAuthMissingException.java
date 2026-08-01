package org.tb.khata.login.auth.exception;

/** X-Internal-Auth header missing on /internal/** call. */
public class InternalAuthMissingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InternalAuthMissingException(String message) {
        super(message);
    }
}
