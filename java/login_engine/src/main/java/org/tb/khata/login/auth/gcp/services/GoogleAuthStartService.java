package org.tb.khata.login.auth.gcp.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.tb.khata.login.auth.OAuthStateGenerator;
import org.tb.khata.login.auth.gcp.GoogleAuthUrlBuilder;

import java.net.URI;

import static org.tb.khata.login.auth.gcp.GCPAuthConstants.*;

@Service
public class GoogleAuthStartService {

    @Autowired
    RedirectionResolver redirectionResolver;

    @Autowired
    OAuthStateGenerator stateGenerator;

    @Autowired
    GoogleAuthUrlBuilder authUrlBuilder;

    @Autowired
    CookieCreator cookieCreator;


    public ResponseEntity<Void> startGoogleAuthService(String redirect) {
        String resolvedRedirect = redirectionResolver.resolveRedirect(redirect);
        String state = stateGenerator.generate();
        String authUrl = authUrlBuilder.build(state, /* forceConsent= */ true);

        ResponseCookie stateCookie = cookieCreator.shortLivedOauthCookie(STATE_COOKIE, state);
        ResponseCookie postLoginCookie = cookieCreator.shortLivedOauthCookie(POST_LOGIN_COOKIE, resolvedRedirect);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authUrl))
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .header(HttpHeaders.SET_COOKIE, postLoginCookie.toString())
                .build();
    }
}
