package io.github.raktimchandra.testharness.gate.api;

import io.github.raktimchandra.testharness.api.auth.Credentials;

/** A user of the Gate system that a test acts as. */
public record GateUser(Credentials credentials, String role) {

    public String username() {
        return credentials.username();
    }

    public boolean isClerk() {
        return "CLERK".equals(role);
    }
}
