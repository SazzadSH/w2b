package com.w2b.walletservice.exceptions;

public class InvalidRequestAuthSignature extends RuntimeException {
    public InvalidRequestAuthSignature(String message) {
        super("Unauthorized request: " + message);
    }
}
