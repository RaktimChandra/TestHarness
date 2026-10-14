package io.github.raktimchandra.gate.api.web;

import io.github.raktimchandra.gate.domain.DomainException;
import io.github.raktimchandra.gate.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every failure to a problem+json body with a stable {@code code}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException e, HttpServletRequest req) {
        return ProblemWriter.problem(e.code(), e.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ProblemWriter.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ProblemWriter.FieldError(f.getField(), f.getDefaultMessage()))
                .sorted(Comparator.comparing(ProblemWriter.FieldError::field))
                .toList();
        return ProblemWriter.problem(ErrorCode.VALIDATION_FAILED, "Request body is invalid", req.getRequestURI(), errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalidParam(ConstraintViolationException e, HttpServletRequest req) {
        List<ProblemWriter.FieldError> errors = e.getConstraintViolations().stream()
                .map(v -> new ProblemWriter.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .sorted(Comparator.comparing(ProblemWriter.FieldError::field))
                .toList();
        return ProblemWriter.problem(ErrorCode.VALIDATION_FAILED, "Request parameters are invalid",
                req.getRequestURI(), errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ProblemDetail unreadable(Exception e, HttpServletRequest req) {
        String detail = e instanceof MethodArgumentTypeMismatchException m
                ? "Parameter '" + m.getName() + "' has an invalid value"
                : e instanceof MissingServletRequestParameterException m
                        ? "Parameter '" + m.getParameterName() + "' is required"
                        : "Request body is missing or is not valid JSON";
        return ProblemWriter.problem(ErrorCode.VALIDATION_FAILED, detail, req.getRequestURI());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail notFound(NoResourceFoundException e, HttpServletRequest req) {
        return ProblemWriter.problem(ErrorCode.NOT_FOUND, "Not found", req.getRequestURI());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail method(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED,
                "Method " + e.getMethod() + " is not supported here");
        pd.setProperty("code", "METHOD_NOT_ALLOWED");
        return pd;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e, HttpServletRequest req) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), e);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error");
        pd.setProperty("code", "INTERNAL_ERROR");
        return pd;
    }
}
