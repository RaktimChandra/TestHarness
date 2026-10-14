package io.github.raktimchandra.testharness.api.auth;

import io.github.raktimchandra.testharness.core.config.Secret;
import java.util.Objects;

/** A username plus a password held as a {@link Secret} so it never prints. */
public record Credentials(String username, Secret password) {

    public Credentials {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
    }

    public static Credentials of(String username, String password) {
        return new Credentials(username, new Secret("password:" + username, password));
    }
}
