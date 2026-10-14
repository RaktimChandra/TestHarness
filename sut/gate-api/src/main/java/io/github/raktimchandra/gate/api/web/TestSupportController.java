package io.github.raktimchandra.gate.api.web;

import io.github.raktimchandra.gate.api.config.GateProperties;
import io.github.raktimchandra.gate.api.config.ShiftableClock;
import io.github.raktimchandra.gate.api.persistence.UserRepository;
import io.github.raktimchandra.gate.domain.MutableBugSwitches;
import io.github.raktimchandra.gate.domain.SeededBug;
import io.github.raktimchandra.gate.domain.ErrorCode;
import io.github.raktimchandra.gate.domain.DomainException;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controls: seeded-bug switches, time travel and throw-away users. Requests only
 * reach this controller when {@code gate.test-support.enabled=true} and the key matches
 * (see TestSupportKeyFilter).
 */
@RestController
@RequestMapping("/api/test-support")
public class TestSupportController {

    private static final Logger log = LoggerFactory.getLogger(TestSupportController.class);

    private final MutableBugSwitches bugs;
    private final ShiftableClock clock;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final GateProperties props;
    private final Views views;
    private final SecureRandom random = new SecureRandom();

    public TestSupportController(MutableBugSwitches bugs, ShiftableClock clock, UserRepository users,
                                 PasswordEncoder encoder, GateProperties props, Views views) {
        this.bugs = bugs;
        this.clock = clock;
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.views = views;
    }

    @GetMapping("/bugs")
    Dtos.BugsView bugs() {
        List<Dtos.BugInfo> available = Arrays.stream(SeededBug.values())
                .map(b -> new Dtos.BugInfo(b, b.description(), b.location())).toList();
        return new Dtos.BugsView(bugs.snapshot().stream().sorted().toList(), available);
    }

    @PutMapping("/bugs")
    Dtos.BugsView setBugs(@Valid @RequestBody Dtos.SetBugsRequest body) {
        bugs.replaceAll(body.active());
        log.warn("Seeded bugs now active: {}", bugs.snapshot());
        return bugs();
    }

    @GetMapping("/clock")
    Dtos.ClockView clock() {
        return new Dtos.ClockView(views.time(clock.instant()), clock.offset().toSeconds());
    }

    @PutMapping("/clock")
    Dtos.ClockView setClock(@Valid @RequestBody Dtos.SetClockRequest body) {
        clock.setOffset(Duration.ofSeconds(body.offsetSeconds()));
        return clock();
    }

    /** Creates an isolated user so a test never shares state with another test. */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    Dtos.CreatedUser createUser(@Valid @RequestBody Dtos.CreateUserRequest body) {
        String username = body.username() != null ? body.username()
                : body.role().name().toLowerCase() + "-" + HexFormat.of().formatHex(randomBytes());
        String password = props.seed().password();
        boolean created = users.insertIfAbsent(username, encoder.encode(password), body.role(),
                "Test " + body.role().name().charAt(0) + body.role().name().substring(1).toLowerCase() + " " + username,
                null);
        if (!created) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Username " + username + " is taken");
        }
        return new Dtos.CreatedUser(username, password, body.role());
    }

    private byte[] randomBytes() {
        byte[] b = new byte[5];
        random.nextBytes(b);
        return b;
    }
}
