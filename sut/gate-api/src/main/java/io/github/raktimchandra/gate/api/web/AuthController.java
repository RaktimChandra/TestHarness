package io.github.raktimchandra.gate.api.web;

import io.github.raktimchandra.gate.api.persistence.UserRecord;
import io.github.raktimchandra.gate.api.security.TokenService;
import io.github.raktimchandra.gate.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final TokenService tokens;
    private final Views views;

    public AuthController(AuthService auth, TokenService tokens, Views views) {
        this.auth = auth;
        this.tokens = tokens;
        this.views = views;
    }

    @PostMapping("/login")
    Dtos.LoginResponse login(@Valid @RequestBody Dtos.LoginRequest body) {
        UserRecord user = auth.authenticate(body.username(), body.password());
        TokenService.IssuedToken token = tokens.issue(user);
        return new Dtos.LoginResponse(token.value(), "Bearer", views.time(token.expiresAt()), views.user(user));
    }

    @GetMapping("/me")
    Dtos.UserView me(@AuthenticationPrincipal Jwt jwt) {
        return views.user(auth.byUsername(jwt.getSubject()));
    }
}
