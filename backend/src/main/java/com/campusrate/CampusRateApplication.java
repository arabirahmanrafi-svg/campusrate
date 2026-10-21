package com.campusrate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// We log users in with our own JWT filter, so Spring's default in-memory user is turned off.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CampusRateApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusRateApplication.class, args);
    }
}
