package kdt.washPrj.washsite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NaverGeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(NaverGeocodingClient.class);

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final String apiUrl;
    private final String clientId;
    private final String clientSecret;
    private final int maxAttempts;
    private final long cacheSeconds;
    private final Map<String, CachedCoordinates> cache = new ConcurrentHashMap<>();

    public NaverGeocodingClient(
            JsonMapper jsonMapper,
            @Value("${naver.maps.geocoding-api-url:https://maps.apigw.ntruss.com/map-geocode/v2/geocode}") String apiUrl,
            @Value("${naver.maps.client-id:}") String clientId,
            @Value("${naver.maps.client-secret:}") String clientSecret,
            @Value("${naver.maps.geocoding-max-attempts:3}") int maxAttempts,
            @Value("${naver.maps.geocoding-cache-seconds:86400}") long cacheSeconds
    ) {
        this.jsonMapper = jsonMapper;
        this.apiUrl = apiUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 5));
        this.cacheSeconds = Math.max(cacheSeconds, 60);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public Optional<Coordinates> geocode(String address) {
        if (address == null || address.isBlank() || !isConfigured()) {
            return Optional.empty();
        }

        String normalizedAddress = address.trim().replaceAll("\\s+", " ");
        CachedCoordinates cached = cache.get(normalizedAddress);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return Optional.of(cached.coordinates());
        }
        if (cached != null) {
            cache.remove(normalizedAddress, cached);
        }

        try {
            String encodedAddress = URLEncoder.encode(normalizedAddress, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "?query=" + encodedAddress))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .header("x-ncp-apigw-api-key-id", clientId)
                    .header("x-ncp-apigw-api-key", clientSecret)
                    .GET()
                    .build();

            HttpResponse<String> response = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                if (response.statusCode() == 200) {
                    break;
                }
                if (!isRetryable(response.statusCode()) || attempt == maxAttempts) {
                    log.warn("Naver geocoding failed with HTTP {} after {} attempt(s).",
                            response.statusCode(), attempt);
                    return Optional.empty();
                }
                Thread.sleep(100L * attempt);
            }

            GeocodeResponse body = jsonMapper.readValue(response.body(), GeocodeResponse.class);
            if (body.addresses() == null || body.addresses().isEmpty()) {
                return Optional.empty();
            }

            GeocodeAddress result = body.addresses().get(0);
            Coordinates coordinates = new Coordinates(
                    Double.parseDouble(result.y()),
                    Double.parseDouble(result.x())
            );
            cache.put(
                    normalizedAddress,
                    new CachedCoordinates(coordinates, Instant.now().plusSeconds(cacheSeconds))
            );
            return Optional.of(coordinates);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Naver geocoding request was interrupted.");
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Naver geocoding request failed: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    public record Coordinates(double latitude, double longitude) {
    }

    public record GeocodeResponse(String status, List<GeocodeAddress> addresses) {
    }

    public record GeocodeAddress(String x, String y) {
    }

    private record CachedCoordinates(
            Coordinates coordinates,
            Instant expiresAt
    ) {
    }
}
