package org.tb.khata.login.auth.exception;

/** X-Internal-Auth header present but doesn't match INTERNAL_SVC_TOKEN. */
public class InternalAuthInvalidException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InternalAuthInvalidException(String message) {
        super(message);
    }
}
