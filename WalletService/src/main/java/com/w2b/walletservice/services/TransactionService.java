package com.w2b.walletservice.services;


import com.w2b.walletservice.domain.W2BTransaction;
import com.w2b.walletservice.enums.TransactionStatus;
import com.w2b.walletservice.repository.jpa.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Service
public class TransactionService {
    @Value("${transaction.cache.ttl}")
    private long redisTTL;
    private final TransactionRepository transactionReposiotry;
    private final RedisTemplate<String, Object> redisTemplate;

    public Optional<W2BTransaction> getTransaction(String transactionId) {
        var transaction = (W2BTransaction) redisTemplate.opsForValue().get(transactionId);
        if (transaction == null) {
            transaction = transactionReposiotry.findByTransactionId(transactionId).orElse(null);
            if (transaction != null) {
                redisTemplate.opsForValue().set(transactionId, transaction, redisTTL, TimeUnit.SECONDS);
                if (!transaction.getStatus().equals(TransactionStatus.SUCCESS)
                        && !transaction.getStatus().equals(TransactionStatus.FAILED)) {
                    redisTemplate.opsForSet().add("transaction:" + transaction.getStatus(), transaction.getTransactionId());
                    redisTemplate.expire("transaction:" + transaction.getStatus(), redisTTL, TimeUnit.SECONDS);
                }
            }
        }
        return Optional.ofNullable(transaction);
    }

    @Transactional
    public W2BTransaction createOrUpdateTransaction(W2BTransaction transaction) {
        transaction = transactionReposiotry.save(transaction);
        redisTemplate.opsForValue().set(transaction.getTransactionId(), transaction,  redisTTL, TimeUnit.SECONDS);
        if (!transaction.getStatus().equals(TransactionStatus.SUCCESS)
                && !transaction.getStatus().equals(TransactionStatus.FAILED)) {
            redisTemplate.opsForSet().add("transaction:" + transaction.getStatus(), transaction.getTransactionId());
            redisTemplate.expire("transaction:" + transaction.getStatus(), redisTTL, TimeUnit.SECONDS);
        }
        return transaction;
    }

    @Transactional
    public void removeTransaction(W2BTransaction transaction) {
        redisTemplate.delete(transaction.getTransactionId());
        if(!transaction.getStatus().equals(TransactionStatus.SUCCESS)
                && transaction.getStatus().equals(TransactionStatus.FAILED)) {
            redisTemplate.opsForSet().remove("transaction:" + transaction.getStatus(), transaction.getTransactionId());
        }
        transactionReposiotry.delete(transaction);
    }

    public List<W2BTransaction> getUnknownTransactions() {
        var unknownSet = redisTemplate.opsForSet().members("transaction:" + TransactionStatus.UNKNOWN);
        List<W2BTransaction> unknownTransactions = new ArrayList<>();
        if (unknownSet != null) {
            unknownSet.forEach(transaction -> {
                var unknowTransaction = (W2BTransaction) redisTemplate.opsForValue().get(transaction);
                if (unknowTransaction != null) unknownTransactions.add(unknowTransaction);
            });
        }
        return unknownTransactions;
    }

    public List<W2BTransaction> retrieveTransactions(TransactionStatus status) {
        return transactionReposiotry.findAllByStatus(status);
    }
}
