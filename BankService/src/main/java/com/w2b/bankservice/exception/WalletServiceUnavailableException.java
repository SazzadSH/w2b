package com.w2b.bankservice.exception;

public class WalletServiceUnavailableException extends RuntimeException {
    public WalletServiceUnavailableException(String message) {
        super("Waller Service is unavailable: " + message);
    }
}
