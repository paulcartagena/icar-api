package com.paulcartagena.icarapi.exception;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String message
) {

    public ErrorResponse(int status, String message) {
        this(Instant.now(), status, message);
    }
}
