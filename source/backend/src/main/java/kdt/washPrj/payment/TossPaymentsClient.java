package kdt.washPrj.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class TossPaymentsClient {

    private final RestClient restClient;
    private final String secretKey;

    public TossPaymentsClient(
            @Value("${tosspayments.api-url:https://api.tosspayments.com}") String apiUrl,
            @Value("${tosspayments.secret-key:}") String secretKey
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
        this.secretKey = secretKey;
    }

    public Map<String, Object> confirm(Map<String, Object> body) {
        return post("/v1/payments/confirm", body, null);
    }

    public Map<String, Object> findByOrderId(String orderId) {
        requireCredentials();
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/v1/payments/orders/{orderId}", orderId)
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return requireResponse(response);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Map.of(
                        "status", "NOT_FOUND",
                        "orderId", orderId
                );
            }
            throw providerError(exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss Payments lookup failed.",
                    exception
            );
        }
    }

    public Map<String, Object> cancel(
            String paymentKey,
            Map<String, Object> body,
            String idempotencyKey
    ) {
        return post(
                "/v1/payments/" + paymentKey + "/cancel",
                body,
                idempotencyKey
        );
    }

    public boolean hasCredentials() {
        return secretKey != null && !secretKey.isBlank();
    }

    private Map<String, Object> post(
            String path,
            Map<String, Object> body,
            String idempotencyKey
    ) {
        requireCredentials();
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .contentType(MediaType.APPLICATION_JSON);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                request.header("Idempotency-Key", idempotencyKey);
            }
            Map<String, Object> response = request
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return requireResponse(response);
        } catch (RestClientResponseException exception) {
            throw providerError(exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss Payments request failed.",
                    exception
            );
        }
    }

    private String authorization() {
        String credentials = Base64.getEncoder().encodeToString(
                (secretKey + ":").getBytes(StandardCharsets.UTF_8)
        );
        return "Basic " + credentials;
    }

    private void requireCredentials() {
        if (!hasCredentials()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Toss Payments secret key is not configured."
            );
        }
    }

    private Map<String, Object> requireResponse(Map<String, Object> response) {
        if (response == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss Payments returned an empty response."
            );
        }
        return response;
    }

    private ResponseStatusException providerError(
            RestClientResponseException exception
    ) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Toss Payments request was rejected. status="
                        + exception.getStatusCode().value(),
                exception
        );
    }
}
