package com.w2b.bankservice.exception;

public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super("Invalid request: " + message);
    }
}
