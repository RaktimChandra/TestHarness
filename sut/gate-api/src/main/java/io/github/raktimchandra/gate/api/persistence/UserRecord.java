package io.github.raktimchandra.gate.api.persistence;

import io.github.raktimchandra.gate.domain.Actor;
import io.github.raktimchandra.gate.domain.Role;

public record UserRecord(long id, String username, String passwordHash, Role role, String displayName, String truckPlate) {

    public Actor toActor() {
        return new Actor(id, username, role);
    }
}
