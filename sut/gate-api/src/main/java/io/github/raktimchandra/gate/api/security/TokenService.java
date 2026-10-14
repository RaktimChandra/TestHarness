package io.github.raktimchandra.gate.api.security;

import io.github.raktimchandra.gate.api.config.GateProperties;
import io.github.raktimchandra.gate.api.persistence.UserRecord;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues signed HS256 access tokens carrying the user id and role. */
@Service
public class TokenService {

    public static final String ISSUER = "gate-appointment";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_UID = "uid";

    private final JwtEncoder encoder;
    private final GateProperties props;

    public TokenService(JwtEncoder encoder, GateProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public IssuedToken issue(UserRecord user) {
        // Wall-clock time, not the shiftable business clock: test time travel must not expire tokens.
        Instant now = Instant.now();
        Instant expires = now.plus(props.jwt().ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.username())
                .issuedAt(now)
                .expiresAt(expires)
                .claim(CLAIM_UID, user.id())
                .claim(CLAIM_ROLE, user.role().name())
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(value, expires);
    }

    public record IssuedToken(String value, Instant expiresAt) {}
}
