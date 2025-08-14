package com.w2b.bankservice.controller;

import com.w2b.bankservice.dto.WalletTransferRequestDTO;
import com.w2b.bankservice.service.BankTransferService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/bank")
public class BankServiceController {

    @Autowired
    private BankTransferService bankTransferService;

    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transfer(@Valid @RequestBody WalletTransferRequestDTO request,
                                                         @RequestHeader HttpHeaders headers) {
        bankTransferService.validateRequest(request.getTransactionId(), headers);
        var response = bankTransferService.transfer(headers.getFirst("idempotency-key"), request);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/transfer/status")
    public Map<String, String> getTransferStatus(@RequestHeader HttpHeaders headers,
            @RequestParam String transactionId) {
        bankTransferService.validateRequest(transactionId, headers);
        return bankTransferService.getTransferStatus(transactionId, headers.getFirst("idempotency-key"));
    }
}
