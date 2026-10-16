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
    private final LoginThrottle throttle;

    public AuthService(UserRepository users, PasswordEncoder encoder, LoginThrottle throttle) {
        this.users = users;
        this.encoder = encoder;
        this.throttle = throttle;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    /**
     * Checks a sign-in from {@code client} (the caller's address). Locked usernames and clients
     * are refused before the password is looked at; see {@link LoginThrottle}.
     */
    public UserRecord authenticate(String username, String password, String client) {
        throttle.check(username, client).ifPresent(lock -> {
            throw new LoginThrottledException(lock.retryAfter());
        });
        UserRecord user = users.findByUsername(username == null ? "" : username.strip()).orElse(null);
        boolean ok = encoder.matches(password == null ? "" : password, user == null ? dummyHash : user.passwordHash());
        if (user == null || !ok) {
            throttle.failed(username, client);
            throw new DomainException(ErrorCode.UNAUTHENTICATED, "Invalid username or password");
        }
        throttle.succeeded(username);
        return user;
    }

    public UserRecord byUsername(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHENTICATED, "Unknown user"));
    }
}
