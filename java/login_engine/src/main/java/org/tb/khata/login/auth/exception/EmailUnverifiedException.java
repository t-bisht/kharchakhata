package org.tb.khata.login.auth.exception;

/** id_token payload has {@code email_verified == false}. Spec §4.3 fourth branch. */
public class EmailUnverifiedException extends AuthFlowException {

    private static final long serialVersionUID = 1L;

    public EmailUnverifiedException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "email_unverified";
    }
}
