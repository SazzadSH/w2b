package com.w2b.bankservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WalletTransferRequestDTO {
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @NotBlank(message = "Wallet ID is required")
    private String fromWalletId;

    @NotBlank(message = "Bank account is required")
    private String toBankAccount;

    @Positive(message = "Amount must be at least 0.01")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private double amount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Invalid currency code")
    private String currency;
}
