package org.tb.khata.login;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("org.tb.khata.login")
public class LoginEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoginEngineApplication.class, args);
    }
}
