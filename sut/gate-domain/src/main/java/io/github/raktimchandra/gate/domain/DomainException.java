package io.github.raktimchandra.gate.domain;

import java.util.Objects;

/** A business-rule violation. Carries a stable {@link ErrorCode}. */
public class DomainException extends RuntimeException {

    private final ErrorCode code;

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ErrorCode code() {
        return code;
    }
}
