package org.tb.khata.login.auth.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tb.khata.login.security.security.TokenCipher;

/**
 * JPA {@code @AttributeConverter} that encrypts on write and decrypts on read via {@link
 * TokenCipher}. Applied on {@link LoginToken#accessToken} and {@link LoginToken#refreshToken}.
 *
 * <p>Made a Spring-managed bean (prototype scope) so Hibernate 6 will inject {@link TokenCipher}
 * — plain-old JPA converters can't hold dependencies otherwise.
 */
@Converter
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final TokenCipher cipher;

    @Autowired
    public EncryptedStringConverter(TokenCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        return cipher.encrypt(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return cipher.decrypt(stored);
    }
}
