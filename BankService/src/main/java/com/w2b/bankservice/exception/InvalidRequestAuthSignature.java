package com.w2b.bankservice.exception;

public class InvalidRequestAuthSignature extends RuntimeException {
    public InvalidRequestAuthSignature(String message) {
        super("Unauthorized request: " + message);
    }
}
