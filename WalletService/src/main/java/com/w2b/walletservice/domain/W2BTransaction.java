package com.w2b.walletservice.domain;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.w2b.walletservice.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "w2b_transactions")
@JsonTypeInfo(
        use = JsonTypeInfo.Id.CLASS,
        include = JsonTypeInfo.As.PROPERTY,
        property = "@class",
        defaultImpl = W2BTransaction.class
)
public class W2BTransaction implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID idempotencyKey = UUID.randomUUID();

    @Column(nullable = false, unique = true, updatable = false)
    @Indexed
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
    @Indexed
    private TransactionStatus status = TransactionStatus.INITIATED;
}