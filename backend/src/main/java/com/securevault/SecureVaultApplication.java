package com.securevault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling (P5.6) — backs PasswordExpiryScheduler's daily sweep; no other scheduled jobs
// exist yet.
@SpringBootApplication
@EnableScheduling
public class SecureVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureVaultApplication.class, args);
    }
}
