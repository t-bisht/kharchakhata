package org.tb.khata.login.auth.exception;

/** Google's {@code /token} endpoint returned non-2xx or was unreachable. Spec §4.3 third branch. */
public class GoogleTokenExchangeFailedException extends AuthFlowException {

    private static final long serialVersionUID = 1L;

    public GoogleTokenExchangeFailedException(String message) {
        super(message);
    }

    public GoogleTokenExchangeFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String errorCode() {
        return "code_exchange_failed";
    }
}
