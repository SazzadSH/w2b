package com.w2b.walletservice.repository.jpa;

import com.w2b.walletservice.domain.W2BTransaction;
import com.w2b.walletservice.enums.TransactionStatus;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends CrudRepository<W2BTransaction, Long> {
    Optional<W2BTransaction> findByTransactionId(String transactionId);
    List<W2BTransaction> findAllByStatus(TransactionStatus status);
}
