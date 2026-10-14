package io.github.raktimchandra.gate.api.security;

import io.github.raktimchandra.gate.domain.Actor;
import io.github.raktimchandra.gate.domain.Role;
import org.springframework.security.oauth2.jwt.Jwt;

/** Converts a validated token into the domain's {@link Actor}. */
public final class CurrentActor {

    private CurrentActor() {}

    public static Actor from(Jwt jwt) {
        Number uid = jwt.getClaim(TokenService.CLAIM_UID);
        return new Actor(uid.longValue(), jwt.getSubject(), Role.valueOf(jwt.getClaimAsString(TokenService.CLAIM_ROLE)));
    }
}
