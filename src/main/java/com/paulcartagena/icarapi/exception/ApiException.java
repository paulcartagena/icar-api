package com.paulcartagena.icarapi.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public static ApiException resourceNotFound(String message) {
        return new ApiException(message, HttpStatus.NOT_FOUND);
    }

    public static ApiException duplicateResource(String message) {
        return new ApiException(message, HttpStatus.CONFLICT);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(message, HttpStatus.BAD_REQUEST);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(message, HttpStatus.UNAUTHORIZED);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(message, HttpStatus.FORBIDDEN);
    }

    public static ApiException conflict(String message) {
        return new ApiException(message, HttpStatus.CONFLICT);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(message, HttpStatus.TOO_MANY_REQUESTS);
    }

    // An external service (e.g. PayPal) failed or answered something unexpected
    public static ApiException badGateway(String message) {
        return new ApiException(message, HttpStatus.BAD_GATEWAY);
    }

    // A feature that depends on configuration that isn't set (e.g. PayPal credentials)
    public static ApiException serviceUnavailable(String message) {
        return new ApiException(message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
