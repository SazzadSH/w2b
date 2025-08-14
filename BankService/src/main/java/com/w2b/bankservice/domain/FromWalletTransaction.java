package com.w2b.bankservice.domain;

import com.w2b.bankservice.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "from_wallet_transactions")
public class FromWalletTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID idempotencyKey;
    @Column(nullable = false, unique = true, updatable = false)
    private String transactionId;
    @Column(nullable = false, updatable = false)
    private String walletId;
    @Column(nullable = false, updatable = false)
    private String bankAccount;
    @Column(nullable = false, updatable = false)
    private Double amount;
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt  = Instant.now();
    @Column(nullable = false)
    private Integer retryCount = 0;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status = TransactionStatus.PENDING;
    private String message;
}
