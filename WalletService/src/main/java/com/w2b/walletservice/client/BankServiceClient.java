package com.w2b.walletservice.client;

import com.w2b.walletservice.dto.BankServiceAPIRequestDTO;
import com.w2b.walletservice.exceptions.BankServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class BankServiceClient {

    @Value("${bank.service.url}")
    private String bankServiceUrl;
    @Value("${bank.service.healthcheck.api}")
    private String healthCheckApi;
    @Value("${bank.transfer.api}")
    private String bankTransferApi;
    @Value("${bank.transfer.status.api}")
    private String bankTransferStatusApi;
    @Value("${bank.transfer.api.request.retry.max-attempts}")
    private Integer maxRetries = 3;
    @Value("${bank.transfer.api.request.retry.wait-duration.in.seconds}")
    private Integer retryDelay = 1;

    private final RestClient restClient;

    private final WebClient webClient = WebClient.builder().build();

    public ClientResponse sendTransferRequest(BankServiceAPIRequestDTO request,
                                              String signature,
                                              String idempotencyKey,
                                              String timestamp) {
        URI uri = UriComponentsBuilder.fromUri(URI.create(bankServiceUrl))
                .path(bankTransferApi)
                .build()
                .toUri();

        return webClient.post()
                .uri(uri)
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Auth-Timestamp", timestamp)
                .header("X-Auth-Signature", signature)
                .bodyValue(request)
                .exchangeToMono(this::responseHandler)
                .retryWhen(RetryPolicies.backoff(maxRetries, retryDelay)) // Retry transfer request with exponential backoff
                .onErrorResume(ex -> {
                    log.error("Error occurred while sending request to bank service. {}", ex.getMessage());
                    log.warn("All retries have failed.");
                    return Mono.error(new BankServiceUnavailableException("No transaction acknowledgement received!"));
                })
                .block();
    }

    private Mono<ClientResponse> responseHandler(ClientResponse response) {
        if (RetryPolicies.isRetryableStatus(response.statusCode())) {
            return response.createException().flatMap(Mono::error);
        } else {
            return Mono.just(response);
        }
    }

    public boolean checkHealth() {
        log.debug("Checking bank service health...");
        URI uri = UriComponentsBuilder.fromUri(URI.create(bankServiceUrl))
                .path(healthCheckApi)
                .build().toUri();
        try {
            return webClient.get()
                    .uri(uri)
                    .retrieve()
                    .toBodilessEntity()
                    .block()
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (Exception e) {
            log.error("Error occurred while checking Bank Service health: {}", e.getMessage());
            return false;
        }
    }

    public ResponseEntity<?> getTransferStatus(String transactionId, String signature,
                                               String idempotencyKey, String timestamp) {
        URI uri = UriComponentsBuilder.fromUri(URI.create(bankServiceUrl))
                .path(bankTransferStatusApi)
                .queryParam("transactionId", transactionId)
                .build().toUri();

        try {
            return restClient.get()
                    .uri(uri)
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Auth-Timestamp", timestamp)
                    .header("X-Auth-Signature", signature)
                    .retrieve()
                    .toEntity(Map.class);
        } catch (RestClientException exception) {
            if (exception instanceof HttpStatusCodeException ex) {
                return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
            }
            throw exception;
        }
    }
}
