package org.tb.khata.login.auth.exception;

/** User pressed "Cancel" on Google's consent screen. Spec §4.3 first branch. */
public class LoginCancelledException extends AuthFlowException {

    private static final long serialVersionUID = 1L;

    public LoginCancelledException() {
        super("User cancelled Google consent");
    }

    @Override
    public String errorCode() {
        return "access_denied";
    }
}
