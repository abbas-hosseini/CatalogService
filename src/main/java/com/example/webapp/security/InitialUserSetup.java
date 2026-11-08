package com.example.webapp.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class InitialUserSetup implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InitialUserSetup.class);
    private final UserAccounts accounts;
    private final Environment environment;
    public InitialUserSetup(UserAccounts accounts, Environment environment) { this.accounts = accounts;this.environment = environment; }
    @Override public void run(ApplicationArguments args) {
        if (accounts.hasUsers()) return;
        String password = environment.getProperty("app.security.bootstrap.password", "");
        if (password.isEmpty()) {
            log.warn("No login account exists. Set APP_ADMIN_PASSWORD and optionally APP_ADMIN_USERNAME, then restart. Application data remains protected.");
            return;
        }
        accounts.createInitialUser(environment.getProperty("app.security.bootstrap.username", "admin"), password);
        log.info("Initial login account created. Remove APP_ADMIN_PASSWORD from the run configuration; existing accounts are not overwritten on startup.");
    }
}
