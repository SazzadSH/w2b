package com.w2b.walletservice.exceptions;

public class BankServiceUnavailableException extends RuntimeException {
    public BankServiceUnavailableException(String message) {
        super("Bank Service unavailable: " + message);
    }
}
