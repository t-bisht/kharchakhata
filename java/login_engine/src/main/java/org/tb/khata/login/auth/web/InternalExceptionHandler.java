package org.tb.khata.login.auth.web;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.tb.khata.login.auth.exception.GoogleAuthRevokedException;
import org.tb.khata.login.auth.exception.InternalAuthInvalidException;
import org.tb.khata.login.auth.exception.InternalAuthMissingException;
import org.tb.khata.login.auth.exception.LoginTokenNotFoundException;
import org.tb.khata.login.auth.exception.UpstreamUnavailableException;
import org.tb.khata.login.auth.exception.UserContextMismatchException;
import org.tb.khata.login.auth.exception.UserContextRequiredException;
import org.tb.khata.login.auth.gcp.contollers.AuthExceptionHandler;

/**
 * Translates internal-boundary exceptions into JSON error bodies per spec §10. Distinct from
 * {@link AuthExceptionHandler} (which redirects for the browser-facing flow) — internal callers
 * are other services, not browsers, so they get machine-readable responses.
 *
 * <p>Scoped to the internal-web package so it does not intercept exceptions from the browser
 * OAuth controllers.
 */
@ControllerAdvice(basePackages = "org.tb.khata.login.auth.web")
public class InternalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(InternalExceptionHandler.class);

    @ExceptionHandler(InternalAuthMissingException.class)
    ResponseEntity<Map<String, String>> onInternalAuthMissing(InternalAuthMissingException e) {
        log.warn("Internal auth missing: {}", e.getMessage());
        return json(HttpStatus.UNAUTHORIZED, "INTERNAL_AUTH_MISSING");
    }

    @ExceptionHandler(InternalAuthInvalidException.class)
    ResponseEntity<Map<String, String>> onInternalAuthInvalid(InternalAuthInvalidException e) {
        log.error("Internal auth invalid: {}", e.getMessage());
        return json(HttpStatus.FORBIDDEN, "INTERNAL_AUTH_INVALID");
    }

    @ExceptionHandler(UserContextRequiredException.class)
    ResponseEntity<Map<String, String>> onUserContextRequired(UserContextRequiredException e) {
        log.warn("User context required: {}", e.getMessage());
        return json(HttpStatus.UNAUTHORIZED, "USER_CONTEXT_REQUIRED");
    }

    @ExceptionHandler(UserContextMismatchException.class)
    ResponseEntity<Map<String, String>> onUserContextMismatch(UserContextMismatchException e) {
        log.warn("User context mismatch: {}", e.getMessage());
        return json(HttpStatus.FORBIDDEN, "USER_CONTEXT_MISMATCH");
    }

    @ExceptionHandler(LoginTokenNotFoundException.class)
    ResponseEntity<Map<String, String>> onNotFound(LoginTokenNotFoundException e) {
        log.warn("{}", e.getMessage());
        return json(HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }

    @ExceptionHandler(GoogleAuthRevokedException.class)
    ResponseEntity<Map<String, String>> onRevoked(GoogleAuthRevokedException e) {
        log.warn("Gmail auth revoked: {}", e.getMessage());
        return json(HttpStatus.UNAUTHORIZED, "GMAIL_AUTH_REVOKED");
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    ResponseEntity<Map<String, String>> onUpstream(UpstreamUnavailableException e) {
        log.error("Google unreachable: {}", e.getMessage(), e);
        return json(HttpStatus.BAD_GATEWAY, "GOOGLE_UNAVAILABLE");
    }

    private static ResponseEntity<Map<String, String>> json(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(Map.of("code", code));
    }
}
