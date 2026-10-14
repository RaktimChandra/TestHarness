package io.github.raktimchandra.testharness.api.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Logs each user in once per JVM and reuses the token, so parallel tests do not hammer the
 * login endpoint (and login itself is tested explicitly, not as a side effect of every test).
 */
public final class TokenCache {

    private final Function<Credentials, String> login;
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    public TokenCache(Function<Credentials, String> login) {
        this.login = login;
    }

    public String tokenFor(Credentials credentials) {
        return tokens.computeIfAbsent(credentials.username(), u -> login.apply(credentials));
    }

    public void invalidate(String username) {
        tokens.remove(username);
    }

    public int size() {
        return tokens.size();
    }
}
