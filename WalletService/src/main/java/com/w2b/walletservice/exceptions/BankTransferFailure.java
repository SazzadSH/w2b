package com.w2b.walletservice.exceptions;

public class BankTransferFailure extends RuntimeException {
    public BankTransferFailure(String message) {
        super("Bank transfer failed: " + message);
    }
}
