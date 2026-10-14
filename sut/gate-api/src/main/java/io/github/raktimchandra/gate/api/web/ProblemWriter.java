package io.github.raktimchandra.gate.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.raktimchandra.gate.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds RFC 9457 problem responses with an extra machine-readable {@code code}. Used by the
 * MVC exception handler and by servlet filters that run before MVC.
 */
@Component
public class ProblemWriter {

    private final ObjectMapper mapper;

    public ProblemWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static ProblemDetail problem(ErrorCode code, String detail, String path) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(code.httpStatus()), detail);
        pd.setType(URI.create("https://github.com/RaktimChandra/TestHarness/blob/main/docs/errors.md#"
                + code.name().toLowerCase().replace('_', '-')));
        pd.setProperty("code", code.name());
        if (path != null) {
            pd.setInstance(URI.create(path));
        }
        return pd;
    }

    public static ProblemDetail problem(ErrorCode code, String detail, String path, List<FieldError> errors) {
        ProblemDetail pd = problem(code, detail, path);
        pd.setProperty("errors", errors);
        return pd;
    }

    public void write(HttpServletRequest req, HttpServletResponse res, ErrorCode code, String detail)
            throws IOException {
        res.setStatus(code.httpStatus());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), problem(code, detail, req.getRequestURI()));
    }

    public record FieldError(String field, String message) {}
}
