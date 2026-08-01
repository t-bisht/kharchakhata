package org.tb.khata.login.auth.exception;

/** Failure writing to {@code login_tokens}. Spec §4.3 last branch — {@code /login?err=db_error}. */
public class TokenPersistenceException extends AuthFlowException {

    private static final long serialVersionUID = 1L;

    public TokenPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String errorCode() {
        return "db_error";
    }
}
