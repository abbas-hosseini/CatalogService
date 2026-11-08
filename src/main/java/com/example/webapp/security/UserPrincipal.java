package com.example.webapp.security;

import java.io.Serializable;
import java.security.Principal;

public record UserPrincipal(long id, String username, long credentialVersion) implements Principal, Serializable {
    @Override public String getName() { return username; }
}
