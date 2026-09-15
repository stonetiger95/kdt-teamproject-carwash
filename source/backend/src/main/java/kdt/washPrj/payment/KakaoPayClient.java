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

import java.util.Map;

@Component
public class KakaoPayClient {

    private final RestClient restClient;
    private final String secretKey;

    public KakaoPayClient(
            @Value("${kakaopay.api-url:https://open-api.kakaopay.com}") String apiUrl,
            @Value("${kakaopay.secret-key:}") String secretKey
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
        this.secretKey = secretKey;
    }

    public Map<String, Object> ready(Map<String, Object> body) {
        return post("/online/v1/payment/ready", body);
    }

    public Map<String, Object> approve(Map<String, Object> body) {
        return post("/online/v1/payment/approve", body);
    }

    public Map<String, Object> order(Map<String, Object> body) {
        return post("/online/v1/payment/order", body);
    }

    public Map<String, Object> cancel(Map<String, Object> body) {
        return post("/online/v1/payment/cancel", body);
    }

    public boolean hasCredentials() {
        return secretKey != null && !secretKey.isBlank();
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        if (!hasCredentials()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "KakaoPay secret key is not configured."
            );
        }

        try {
            Map<String, Object> response = restClient.post()
                    .uri(path)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "SECRET_KEY " + secretKey
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "KakaoPay returned an empty response."
                );
            }
            return response;
        } catch (RestClientResponseException exception) {
            String providerDetail = responseDetail(exception);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "KakaoPay request was rejected. status="
                            + exception.getStatusCode().value()
                            + (providerDetail.isBlank()
                            ? ""
                            : ", response=" + providerDetail),
                    exception
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "KakaoPay request failed.",
                    exception
            );
        }
    }

    private String responseDetail(RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }

        String normalized = responseBody
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        return normalized.length() <= 800
                ? normalized
                : normalized.substring(0, 800);
    }
}
