package org.tb.khata.login.auth.web;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.tb.khata.login.auth.exception.AuthFlowException;
import org.tb.khata.login.auth.exception.CsrfMismatchException;
import org.tb.khata.login.auth.exception.EmailUnverifiedException;
import org.tb.khata.login.auth.exception.GoogleTokenExchangeFailedException;
import org.tb.khata.login.auth.exception.IdTokenMalformedException;
import org.tb.khata.login.auth.exception.LoginCancelledException;
import org.tb.khata.login.auth.exception.TokenPersistenceException;

/**
 * Translates {@link AuthFlowException}s thrown by {@link GoogleAuthController} into {@code 302
 * /login?err=<code>} redirects, per spec §4.3 and §10.
 *
 * <p>Scoped to controllers under {@code org.tb.khata.login.auth.web} so a future {@code
 * /internal/**} advice can return JSON error bodies without conflicting with these HTML-style
 * redirects.
 *
 * <p>Log-level policy matches spec §10:
 *
 * <ul>
 *   <li>INFO — user-driven ({@link LoginCancelledException})
 *   <li>WARN — user or CSRF issues ({@link CsrfMismatchException}, {@link
 *       EmailUnverifiedException})
 *   <li>ERROR — upstream / infra failures ({@link GoogleTokenExchangeFailedException}, {@link
 *       IdTokenMalformedException})
 * </ul>
 */
@ControllerAdvice(basePackages = "org.tb.khata.login.auth.web")
public class AuthExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);
    private static final String LOGIN_PATH = "/login";

    @ExceptionHandler(LoginCancelledException.class)
    ResponseEntity<Void> onCancelled(LoginCancelledException e) {
        log.info("Login cancelled by user");
        return redirect(e);
    }

    @ExceptionHandler({CsrfMismatchException.class, EmailUnverifiedException.class})
    ResponseEntity<Void> onUserOrCsrfIssue(AuthFlowException e) {
        log.warn("Auth flow rejected [{}]: {}", e.errorCode(), e.getMessage());
        return redirect(e);
    }

    @ExceptionHandler({
        GoogleTokenExchangeFailedException.class,
        IdTokenMalformedException.class,
        TokenPersistenceException.class
    })
    ResponseEntity<Void> onUpstreamFailure(AuthFlowException e) {
        log.error("Auth flow upstream failure [{}]: {}", e.errorCode(), e.getMessage(), e);
        return redirect(e);
    }

    private static ResponseEntity<Void> redirect(AuthFlowException e) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(LOGIN_PATH + "?err=" + e.errorCode()))
                .build();
    }
}
