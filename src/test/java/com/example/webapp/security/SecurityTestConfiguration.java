package com.example.webapp.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.security.SecureRandom;
import java.time.Clock;

/** Deterministic image generation only in tests; production has no CAPTCHA bypass switch. */
@TestConfiguration
public class SecurityTestConfiguration {
    @Bean @Primary CaptchaService testCaptchaService() {
        return new CaptchaService(new SecureRandom() { @Override public int nextInt(int bound) { return 0; } }, Clock.systemUTC());
    }
}
