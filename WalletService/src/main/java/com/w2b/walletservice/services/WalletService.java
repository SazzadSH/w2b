package com.w2b.walletservice.services;

import com.w2b.walletservice.client.BankServiceClient;
import com.w2b.walletservice.domain.W2BTransaction;
import com.w2b.walletservice.domain.Wallet;
import com.w2b.walletservice.dto.BankServiceAPIRequestDTO;
import com.w2b.walletservice.dto.BankTransferRequestDTO;
import com.w2b.walletservice.dto.CallbackRequestBodyDTO;
import com.w2b.walletservice.enums.TransactionStatus;
import com.w2b.walletservice.exceptions.*;
import com.w2b.walletservice.repository.jpa.TransactionRepository;
import com.w2b.walletservice.repository.jpa.WalletRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class WalletService {
    @Value("${bank.service.shared.secret}")
    private String sharedSecret;

    private final WalletRepository walletRepository;
    private final BankServiceClient bankServiceClient;
    private final TransactionRepository transactionReposiotry;
    private final TransactionService transactionService;

    public Wallet validateTransferRequest(@Valid BankTransferRequestDTO request) {
        //Handles duplicate transaction request
        transactionService.getTransaction(request.getTransactionId())
                .ifPresent(transaction -> {
                    String message = "Transfer request accepted. Transaction is in processing";
                    if(transaction.getStatus().equals(TransactionStatus.UNKNOWN))
                        message = "Bank Service Unavailable: No transaction acknowledgement received! Transfer will be processed later.";
                    else if (transaction.getStatus().equals(TransactionStatus.SUCCESS))
                        message = "Transaction was successful. Balance has been transferred to the bank service.";
                    else if (transaction.getStatus().equals(TransactionStatus.FAILED))
                        message = "Transaction was not successful. Try again a new transaction.";
                    throw new DuplicateTransactionException(message);
                });

        var wallet = walletRepository.findByWalletId(request.getWalletId())
                .orElseThrow(() -> new WalletNotFoundException(request.getWalletId()));
        if (!wallet.getCurrency().equals(request.getCurrency()))
            throw new InvalidRequestException("Currency did not match!"); //
        return wallet;
    }

    /**
     * Critial Fallback Scenarios:
     * 1. What if service crashes after sending request but crashes before receiving response.
     * Transaction would rollback wallet debit but bank credit will happen.
     * 2. What if Bank Service doesn't have trace of transaction while Wallet waits for confirmation
     * 3. How much hour should wallet service wait if Bank Service remains unavailable.
     * **/
    @Transactional
    public ResponseEntity<String> processTransaction(Wallet wallet, BankTransferRequestDTO request) {
        if (!bankServiceClient.checkHealth()) throw new BankServiceUnavailableException("Try again later.");
        var transaction = buildBankTransferEntity(request);
        var signature = signTransferRequest(transaction.getIdempotencyKey().toString(),
                transaction.getCreatedAt().toString(),
                request.getTransactionId());  // Generates hmac for request authentication signature
        wallet.debit(BigDecimal.valueOf(request.getAmount())); // Deducts amount from wallet
        walletRepository.save(wallet);
        try {
            var response = bankServiceClient.sendTransferRequest(buildRequestBody(request),
                    signature, transaction.getIdempotencyKey().toString(),
                    transaction.getCreatedAt().toString());  // Sends transfer request to Bank Service
            if (response.statusCode().is2xxSuccessful()) {
                log.debug("Successfully processed transaction");
                transaction.setStatus(TransactionStatus.PENDING); // Changes transaction status = PENDING
                transactionService.createOrUpdateTransaction(transaction);
                return ResponseEntity.status(HttpStatus.OK).body("Transfer request accepted. Transaction is in processing.");
            } else {
                log.debug("Failed to process transaction. Rolling back transaction");
                wallet.credit(BigDecimal.valueOf(request.getAmount()));
                walletRepository.save(wallet);
                transactionService.removeTransaction(transaction);
                return ResponseEntity.status(response.statusCode()).body("Bank Transfer request failed");
            }
        } catch (BankServiceUnavailableException ex) {
            log.debug("Bank transfer service unavailable");
            transaction.setStatus(TransactionStatus.UNKNOWN); // Marks status = UNKNOWN due to Bank Service response failure
            transactionService.createOrUpdateTransaction(transaction);
            // fallback strategy
            return ResponseEntity.status(HttpStatus.OK).body(ex.getMessage() + " Transfer will be processed later.");
        }
    }

    private W2BTransaction buildBankTransferEntity(BankTransferRequestDTO request) {
        var bankTransfer = new W2BTransaction();
        bankTransfer.setTransactionId(request.getTransactionId());
        bankTransfer.setWalletId(request.getWalletId());
        bankTransfer.setBankAccount(request.getToBankAccount());
        bankTransfer.setAmount(request.getAmount());
        bankTransfer.setCurrency(request.getCurrency());
        return bankTransfer;
    }

    private BankServiceAPIRequestDTO buildRequestBody(BankTransferRequestDTO request) {
        return BankServiceAPIRequestDTO.builder()
                .transactionId(request.getTransactionId())
                .fromWalletId(request.getWalletId())
                .toBankAccount(request.getToBankAccount())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .build();
    }

    public W2BTransaction validateConfirmationRequest(CallbackRequestBodyDTO request) {
        var bankTransfer = transactionService.getTransaction(request.getTransactionId())
                .orElseThrow(() -> new InvalidRequestException("Invalid transaction ID"));
        if (bankTransfer.getStatus().equals(TransactionStatus.SUCCESS)
                || bankTransfer.getStatus().equals(TransactionStatus.FAILED)) {
            throw new InvalidRequestException("Transfer confirmation already received!");
        }
        return bankTransfer;
    }

    // Confirms Successful transaction / Restore wallet amount and mark as failed
    @Transactional
    public void confirmTransfer(W2BTransaction transaction, String status) {
        if (status.trim().equalsIgnoreCase(TransactionStatus.SUCCESS.toString()))
            transaction.setStatus(TransactionStatus.SUCCESS);
        else if (status.trim().equalsIgnoreCase(TransactionStatus.FAILED.toString())){ // transaction rollback
            var wallet = walletRepository.findByWalletId(transaction.getWalletId())
                    .orElseThrow(() -> new WalletNotFoundException(transaction.getWalletId()));
            wallet.credit(BigDecimal.valueOf(transaction.getAmount()));
            transaction.setStatus(TransactionStatus.FAILED);
        }
        transaction.setUpdatedAt(Instant.now());
        transactionService.createOrUpdateTransaction(transaction);
    }

    public String signTransferRequest(String idempotencyKey, String timestamp, String transactionId) {
        String data = idempotencyKey + timestamp + transactionId;
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, sharedSecret).hmacHex(data);
    }

    //Validates HMAC signature in request header
    public void verifySignature(String transactionId, HttpHeaders headers) {
        String signature = headers.getFirst("X-Auth-Signature");
        String timestamp = headers.getFirst("X-Auth-Timestamp");
        String idempotencyKey = headers.getFirst("idempotency-key");
        String data = idempotencyKey + timestamp + transactionId;
        if (signature == null ||
                !signature.equals(new HmacUtils(HmacAlgorithms.HMAC_SHA_256, sharedSecret).hmacHex(data))) {
            log.error("Signature verification failed!");
            throw new InvalidRequestAuthSignature("Invalid signature!");
        }
    }

    public List<W2BTransaction> retrieveUnknownTransactions() {
        return transactionReposiotry.findAllByStatus(TransactionStatus.UNKNOWN);
    }
}
