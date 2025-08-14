package com.w2b.bankservice.service;

import com.w2b.bankservice.client.WalletServiceClient;
import com.w2b.bankservice.domain.FromWalletTransaction;
import com.w2b.bankservice.dto.WalletTransferRequestDTO;
import com.w2b.bankservice.enums.TransactionStatus;
import com.w2b.bankservice.exception.InvalidRequestAuthSignature;
import com.w2b.bankservice.exception.InvalidRequestException;
import com.w2b.bankservice.repository.BankAccountRepository;
import com.w2b.bankservice.repository.FromWalletTransactionRepository;
import com.w2b.bankservice.service.processor.TransferProcessProducer;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
public class BankTransferService {
    @Value("${wallet.service.shared.secret}")
    private String sharedSecret;
    @Value("${wallet.service.request.allowed.lifetime}")
    private long requestLifetime;

    @Autowired
    private FromWalletTransactionRepository fromWalletTransactionRepository;
    @Autowired
    private TransferProcessProducer transferProcessProducer;
    @Autowired
    private BankAccountRepository bankAccountRepository;
    @Autowired
    private WalletServiceClient walletServiceClient;

    //if request exists, returns current status. else creates a new process, enqueues and returns acknowledgement
    @Transactional
    public Map<String, Object> transfer(String idempotencyKey, WalletTransferRequestDTO request) {
        var transaction = fromWalletTransactionRepository.findByTransactionId(request.getTransactionId());
        if (transaction != null && !transaction.getIdempotencyKey().toString().equals(idempotencyKey))
            throw new InvalidRequestException("Request header contains invalid idempotency key");
        if (transaction == null){
            transaction = fromWalletTransactionRepository.save(buildFromWalletTransactionEntity(request, idempotencyKey));
            transferProcessProducer.produce(transaction.getTransactionId()); // Enqueue for asynchronous processing
        }
        return Map.ofEntries(Map.entry("transactionId", transaction.getTransactionId()),
                Map.entry("status", transaction.getStatus().toString()));
    }

    public void validateRequest(String transactionId, HttpHeaders headers) {
        verifyRequestTimestamp(headers.getFirst("X-Auth-Timestamp")); // validates request's lifetime is valid
        verifyRequestSignature(headers, transactionId); // validates HMAC signature
    }

    private FromWalletTransaction buildFromWalletTransactionEntity(WalletTransferRequestDTO request, String idempotencyKey) {
        var fromWalletTransaction = new FromWalletTransaction();
        fromWalletTransaction.setIdempotencyKey(UUID.fromString(idempotencyKey));
        fromWalletTransaction.setTransactionId(request.getTransactionId());
        fromWalletTransaction.setWalletId(request.getFromWalletId());
        fromWalletTransaction.setBankAccount(request.getToBankAccount());
        fromWalletTransaction.setAmount(request.getAmount());
        fromWalletTransaction.setCurrency(request.getCurrency());
        return fromWalletTransaction;
    }

    // Timestamp validation: must contain in header, must be valid and within accepted lifetime (e.g., 3 minutes)
    private void verifyRequestTimestamp(String timestamp) {
        log.debug("timestamp: " + timestamp);
        if (timestamp == null) throw new InvalidRequestException("Request timestamp does not exist");
        if (requestLifetime == 0) requestLifetime = 180;
        long requestEpochSeconds = Instant.parse(timestamp).getEpochSecond();
        long currentEpochSecond = Instant.now().getEpochSecond();
        long lifeTime = Instant.ofEpochSecond(requestEpochSeconds).plusSeconds(requestLifetime).getEpochSecond();
        if (!(requestEpochSeconds <= currentEpochSecond)) throw new InvalidRequestException("Request timestamp is invalid");
        if (!(currentEpochSecond <= lifeTime)) throw new InvalidRequestException("Request lifetime expired");
    }

    // Ensures valid HMAC signature exists in the request header
    private void verifyRequestSignature(HttpHeaders headers, String transactionId) {
        String idempotencyKey = headers.getFirst("idempotency-key");
        if(idempotencyKey == null || idempotencyKey.trim().isEmpty()) throw new InvalidRequestException("idempotency-key header is missing");
        String timestamp = headers.getFirst("X-Auth-Timestamp");
        String signature = headers.getFirst("X-Auth-Signature");
        String data = idempotencyKey + timestamp + transactionId;
        if (signature == null || signature.trim().isEmpty() ||
                !signature.equals(new HmacUtils(HmacAlgorithms.HMAC_SHA_256, sharedSecret).hmacHex(data))) {
            log.info("signature mismatch");
            throw new InvalidRequestAuthSignature("Invalid request signature!");
        }
    }

    /**starts processing the transfer within a 'transactional' block. if an error occurs while processing,
     * ensures automatic rollback **/
    @Transactional
    public FromWalletTransaction processTransaction(FromWalletTransaction transaction) {
        transaction.setUpdatedAt(Instant.now());
        transaction.setStatus(TransactionStatus.FAILED);
        var bankAccount = bankAccountRepository.findByAccountNumber(transaction.getBankAccount());
        if (bankAccount == null){
            transaction.setMessage("Invalid bank account");
        } else if (!bankAccount.getCurrency().equalsIgnoreCase(transaction.getCurrency())) {
            transaction.setMessage("Invalid currency");
        } else {
            transaction.setStatus(TransactionStatus.SUCCESS);
            transaction.setMessage("Transfer successful");
            bankAccount.credit(BigDecimal.valueOf(transaction.getAmount()));
            bankAccountRepository.save(bankAccount);
        }
        return fromWalletTransactionRepository.save(transaction);
    }

    public void updateTransactionStatus(FromWalletTransaction transaction, TransactionStatus status) {
        transaction.setUpdatedAt(Instant.now());
        transaction.setStatus(status);
        fromWalletTransactionRepository.save(transaction);
    }

    // notifies Wallet service through webhook callback API, if status == Success / Failed
    public void notifyWalletService(FromWalletTransaction transaction) {
        if (transaction.getStatus().equals(TransactionStatus.SUCCESS)
                || transaction.getStatus().equals(TransactionStatus.FAILED)) {
            String timestamp = Instant.now().toString();
            String signature = signCallbackRequest(transaction.getIdempotencyKey().toString(),
                    transaction.getTransactionId(), timestamp);
            var requestBody = buildCallbackRequestBody(transaction.getTransactionId(),
                    transaction.getIdempotencyKey().toString(), transaction.getUpdatedAt().toString(),
                    transaction.getStatus().toString(), transaction.getMessage());
            walletServiceClient.sendWebhookCallback(requestBody, signature, timestamp);
        }
    }

    private String signCallbackRequest(String referenceId, String transactionId, String timestamp) {
        String data = referenceId + timestamp + transactionId;
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, sharedSecret).hmacHex(data);
    }

    private Map<String, String> buildCallbackRequestBody(String transactionId, String referenceId,
                                                         String timestamp, String status, String message) {
        Map<String, String> callbackRequestBody = new HashMap<>();
        callbackRequestBody.put("transactionId", transactionId);
        callbackRequestBody.put("referenceId", referenceId);
        callbackRequestBody.put("processedAt", timestamp);
        callbackRequestBody.put("status", status);
        callbackRequestBody.put("message", message);
        return callbackRequestBody;
    }

    public FromWalletTransaction retrievePendingTransaction(String transactionId) {
        var tranasaction = fromWalletTransactionRepository.findByTransactionIdAndStatus(transactionId,
                TransactionStatus.PENDING);
        if (tranasaction.getStatus().equals(TransactionStatus.PENDING)) return tranasaction;
        return null;
    }

    // Get the current status of an existing transfer
    public Map<String, String> getTransferStatus(String transactionId, String idempotencyKey) {
        var transaction = fromWalletTransactionRepository.findByTransactionIdAndIdempotencyKey(transactionId,
                UUID.fromString(idempotencyKey)).orElseThrow(
                        () -> new NoSuchElementException("No transaction found: " + transactionId));
        return Map.ofEntries(
                Map.entry("transactionId", transaction.getTransactionId()),
                Map.entry("status", transaction.getStatus().toString()));
    }
}
