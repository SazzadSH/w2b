package com.w2b.walletservice.controller;

import com.w2b.walletservice.dto.BankTransferRequestDTO;
import com.w2b.walletservice.dto.CallbackRequestBodyDTO;
import com.w2b.walletservice.services.WalletService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    @Autowired
    private WalletService walletService;

    @PostMapping("/transfer-to-bank")
    public ResponseEntity<String> transfer(@Valid @RequestBody BankTransferRequestDTO request) {
        var wallet = walletService.validateTransferRequest(request); // needs additional logic
        return walletService.processTransaction(wallet, request);
    }

    @PostMapping("/transfer-callback")
    public ResponseEntity<String> webHookCallback(@Valid @RequestBody CallbackRequestBodyDTO request,
                                                  @RequestHeader HttpHeaders headers) {
        log.debug("Received WebHook Callback Request: {}", request);
        walletService.verifySignature(request.getTransactionId(), headers);
        var transaction = walletService.validateConfirmationRequest(request);
        if (transaction != null) walletService.confirmTransfer(transaction, request.getStatus());
        return ResponseEntity.ok().build();
    }
}
