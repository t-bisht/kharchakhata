package org.tb.khata.login.auth.exception;

/**
 * {@code ?state=} query param does not match {@code kk_oauth_state} cookie — CSRF suspected. Spec
 * §4.3 second branch.
 */
public class CsrfMismatchException extends AuthFlowException {

    private static final long serialVersionUID = 1L;

    public CsrfMismatchException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "state_invalid";
    }
}
