package com.w2b.bankservice.client;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public class RetryPolicies {
    private RetryPolicies() {}

    private static final Set<HttpStatusCode> RETRYABLE_STATUSES = Set.of(
            HttpStatusCode.valueOf(HttpStatus.REQUEST_TIMEOUT.value()),      // 408
            HttpStatusCode.valueOf(HttpStatus.TOO_MANY_REQUESTS.value()),    // 429
            HttpStatusCode.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()),// 500
            HttpStatusCode.valueOf(HttpStatus.BAD_GATEWAY.value()),          // 502
            HttpStatusCode.valueOf(HttpStatus.SERVICE_UNAVAILABLE.value()),  // 503
            HttpStatusCode.valueOf(HttpStatus.GATEWAY_TIMEOUT.value())       // 504
    );

    public static Retry backoff(int maxRetries, int retryDelay) {
        return Retry
                .backoff(maxRetries, Duration.ofSeconds(retryDelay))  // 3 s, 6 s, 12 s, 24 s, 48 s
                .maxBackoff(Duration.ofSeconds(90))                // cap the wait
                .jitter(0.50)                            // ±50 % to prevent thundering‑herd
                .filter(RetryPolicies::isRetryable)                // only for “worth‑retrying” errors
                .onRetryExhaustedThrow((spec, signal) ->           // bubble the last failure
                        signal.failure());
    }

    public static boolean isRetryableStatus(HttpStatusCode status) {
        return RETRYABLE_STATUSES.contains(status);
    }

    public static boolean isRetryable(Throwable t) {
        return t instanceof TimeoutException
                || t instanceof ConnectException
                || t instanceof SSLException
                || (t instanceof WebClientResponseException ex
                && isRetryableStatus(ex.getStatusCode()));
    }
}
