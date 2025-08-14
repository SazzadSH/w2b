package com.w2b.walletservice.exceptions;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String walletId, Object amount, Object balance) {
        super("Wallet %s lacks funds: requested: %s, available balance: %s".formatted(walletId, amount, balance));
    }
}
