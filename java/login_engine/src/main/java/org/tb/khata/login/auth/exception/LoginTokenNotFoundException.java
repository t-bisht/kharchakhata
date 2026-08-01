package org.tb.khata.login.auth.exception;

/** No login_tokens row for the requested user_id. */
public class LoginTokenNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LoginTokenNotFoundException(String userId) {
        super("login_tokens row not found for user_id=" + userId);
    }
}
