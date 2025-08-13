package com.w2b.bankservice.repository;

import com.w2b.bankservice.domain.FromWalletTransaction;
import com.w2b.bankservice.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FromWalletTransactionRepository extends JpaRepository<FromWalletTransaction, Long> {
    FromWalletTransaction findByTransactionId(String transactionId);
    FromWalletTransaction findByTransactionIdAndStatus(String transactionId, TransactionStatus status);
    Optional<FromWalletTransaction> findByTransactionIdAndIdempotencyKey(String transactionId, UUID idempotencyKey);
}
