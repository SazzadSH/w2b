package com.w2b.walletservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class CallbackRequestBodyDTO {
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    @NotBlank(message = "Reference ID is required")
    private String referenceId;
    @NotNull
    private Instant processedAt;
    @NotBlank(message = "Valid status required")
    private String status;
    private String message;
}
