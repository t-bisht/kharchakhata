package org.tb.khata.login.auth.exception;

/** The id_token from Google is not a well-formed JWT / valid JSON. Treated as a bad exchange. */
public class IdTokenMalformedException extends AuthFlowException {

    private static final long serialVersionUID = 1L;

    public IdTokenMalformedException(String message) {
        super(message);
    }

    public IdTokenMalformedException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String errorCode() {
        return "code_exchange_failed";
    }
}
