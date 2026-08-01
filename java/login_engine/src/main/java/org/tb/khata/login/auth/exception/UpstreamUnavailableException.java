package org.tb.khata.login.auth.exception;

/** Google /token unreachable or 5xx during refresh. */
public class UpstreamUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
