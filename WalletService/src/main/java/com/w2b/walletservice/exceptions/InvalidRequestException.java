package com.w2b.walletservice.exceptions;

public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super("Invalid request: " + message);
    }
}
