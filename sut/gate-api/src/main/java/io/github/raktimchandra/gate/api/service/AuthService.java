package io.github.raktimchandra.gate.api.service;

import io.github.raktimchandra.gate.api.persistence.UserRecord;
import io.github.raktimchandra.gate.api.persistence.UserRepository;
import io.github.raktimchandra.gate.domain.DomainException;
import io.github.raktimchandra.gate.domain.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    /** Hash compared against when the user does not exist, so timing does not reveal valid usernames. */
    private final String dummyHash;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    public UserRecord authenticate(String username, String password) {
        UserRecord user = users.findByUsername(username == null ? "" : username.strip()).orElse(null);
        boolean ok = encoder.matches(password == null ? "" : password, user == null ? dummyHash : user.passwordHash());
        if (user == null || !ok) {
            throw new DomainException(ErrorCode.UNAUTHENTICATED, "Invalid username or password");
        }
        return user;
    }

    public UserRecord byUsername(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHENTICATED, "Unknown user"));
    }
}
