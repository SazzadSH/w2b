package com.w2b.walletservice.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@Builder
@ToString
public class BankServiceAPIRequestDTO {
    private String transactionId;
    private String fromWalletId;
    private String toBankAccount;
    private double amount;
    private String currency;
}
