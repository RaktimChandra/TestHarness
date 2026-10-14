package io.github.raktimchandra.gate.domain;

import java.util.Objects;

/** The authenticated user performing an action. */
public record Actor(long userId, String username, Role role) {

    public Actor {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(role, "role");
    }

    public boolean isClerk() {
        return role == Role.CLERK;
    }
}
