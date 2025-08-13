package com.w2b.bankservice.client;

import com.w2b.bankservice.exception.WalletServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
public class WalletServiceClient {

    @Value("${wallet.service.baseurl}")
    private String walletServiceBaseUrl;
    @Value("${wallet.service.callback.api.url}")
    private String walletServiceCallbackUrl;
    @Value("${wallet.service.callback.api.request.retry.max-attempts}")
    private int maxRetries = 3;
    @Value("${wallet.service.callback.api.request.retry.wait-duration.in.seconds}")
    private int retryDelay = 1;

    private final WebClient webClient = WebClient.builder()
            .baseUrl(walletServiceBaseUrl)
            .build();

    public void sendWebhookCallback(Map<String, String> request, String signature, String timestamp) {
        var response = webClient.post()
                .uri(walletServiceBaseUrl + walletServiceCallbackUrl)
                .header("Idempotency-Key", request.get("referenceId"))
                .header("X-Auth-Timestamp", timestamp)
                .header("X-Auth-Signature", signature)
                .bodyValue(request)
                .exchangeToMono(this::responseHandler) // retry
                .retryWhen(RetryPolicies.backoff(maxRetries, retryDelay)) // backoff
                .onErrorResume(ex -> { // fallback
                    log.error("Error occurred while notifying transfer status to wallet service. {}", ex.getMessage());
                    log.warn("All retries have failed.");
                    return Mono.error(new WalletServiceUnavailableException("Failed to notify through callback"));
                })
                .block();

        if (response != null && !response.statusCode().is2xxSuccessful()) {
            //DLQ
            // Alert monitoring
        }
    }

    private Mono<ClientResponse> responseHandler(ClientResponse response) {
        if (RetryPolicies.isRetryableStatus(response.statusCode())) {
            return response.createException().flatMap(Mono::error);
        } else {
            return Mono.just(response);
        }
    }
}
