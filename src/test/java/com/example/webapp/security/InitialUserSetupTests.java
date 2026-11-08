package com.example.webapp.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.mockito.Mockito.*;

class InitialUserSetupTests {
    @Test void noPasswordNeverCreatesADefaultAccount() {
        var accounts=mock(UserAccounts.class);
        new InitialUserSetup(accounts,new MockEnvironment()).run(null);
        verify(accounts).hasUsers();verifyNoMoreInteractions(accounts);
    }
    @Test void existingAccountsAreNotOverwrittenByStartupEnvironment() {
        var accounts=mock(UserAccounts.class);when(accounts.hasUsers()).thenReturn(true);
        new InitialUserSetup(accounts,new MockEnvironment().withProperty("app.security.bootstrap.password","ignored")).run(null);
        verify(accounts).hasUsers();verifyNoMoreInteractions(accounts);
    }
    @Test void configuredInitialPasswordIsSentThroughAccountCreationValidation() {
        var accounts=mock(UserAccounts.class);
        new InitialUserSetup(accounts,new MockEnvironment().withProperty("app.security.bootstrap.username","owner")
            .withProperty("app.security.bootstrap.password",SecurityTestClient.PASSWORD)).run(null);
        verify(accounts).createInitialUser("owner",SecurityTestClient.PASSWORD);
    }
}
