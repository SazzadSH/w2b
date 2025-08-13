package com.w2b.walletservice.exceptions;

public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(String id) {
        super("Wallet " + id + " not found");
    }
}
