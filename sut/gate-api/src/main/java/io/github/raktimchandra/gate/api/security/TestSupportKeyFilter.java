package io.github.raktimchandra.gate.api.security;

import io.github.raktimchandra.gate.api.config.GateProperties;
import io.github.raktimchandra.gate.api.web.ProblemWriter;
import io.github.raktimchandra.gate.domain.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards /api/test-support. Disabled entirely unless {@code gate.test-support.enabled=true};
 * when enabled, requests must carry the configured key in {@code X-Test-Support-Key}.
 */
@Component
public class TestSupportKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Test-Support-Key";

    private final GateProperties props;
    private final ProblemWriter problems;

    public TestSupportKeyFilter(GateProperties props, ProblemWriter problems) {
        this.props = props;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/test-support");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!props.testSupport().enabled()) {
            problems.write(req, res, ErrorCode.NOT_FOUND, "Not found");
            return;
        }
        String expected = props.testSupport().key();
        String given = req.getHeader(HEADER);
        if (expected.isEmpty() || given == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            problems.write(req, res, ErrorCode.FORBIDDEN, "Missing or wrong " + HEADER);
            return;
        }
        chain.doFilter(req, res);
    }
}
