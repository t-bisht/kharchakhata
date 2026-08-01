package org.tb.khata.login.auth.exception;

/**
 * Base class for OAuth-flow exceptions that the browser-facing advice translates into a {@code 302
 * /login?err=<code>} redirect.
 *
 * <p>Each subclass carries a fixed {@link #errorCode()} matching the {@code err=} tokens defined
 * in spec §4.3 and §10.
 */
public abstract class AuthFlowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected AuthFlowException(String message) {
        super(message);
    }

    protected AuthFlowException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Short token that the SPA reads off the {@code ?err=} query param. */
    public abstract String errorCode();
}
