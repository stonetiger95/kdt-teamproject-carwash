package kdt.washPrj.washsite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import kdt.washPrj.washsite.dto.DrivingRoutePointDTO;
import kdt.washPrj.washsite.dto.DrivingRouteResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class NaverDirectionsClient {

    private static final Logger log = LoggerFactory.getLogger(NaverDirectionsClient.class);

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final String apiUrl;
    private final String clientId;
    private final String clientSecret;

    public NaverDirectionsClient(
            JsonMapper jsonMapper,
            @Value("${naver.maps.directions-api-url:https://maps.apigw.ntruss.com/map-direction/v1/driving}") String apiUrl,
            @Value("${naver.maps.client-id:}") String clientId,
            @Value("${naver.maps.client-secret:}") String clientSecret
    ) {
        this.jsonMapper = jsonMapper;
        this.apiUrl = apiUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public DrivingRouteResponseDTO getDrivingRoute(
            double startLatitude,
            double startLongitude,
            double goalLatitude,
            double goalLongitude
    ) {
        validateConfigured();
        validateCoordinate(startLatitude, startLongitude);
        validateCoordinate(goalLatitude, goalLongitude);

        String start = startLongitude + "," + startLatitude;
        String goal = goalLongitude + "," + goalLatitude;
        URI requestUri = URI.create(
                apiUrl + "?start=" + start
                        + "&goal=" + goal
                        + "&option=traoptimal"
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(requestUri)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("x-ncp-apigw-api-key-id", clientId)
                .header("x-ncp-apigw-api-key", clientSecret)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() != 200) {
                log.warn("Naver directions failed with HTTP {}.", response.statusCode());
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "네이버 길찾기 요청에 실패했습니다."
                );
            }
            return parseResponse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "네이버 길찾기 요청이 중단되었습니다.",
                    exception
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Naver directions request failed: {}", exception.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "네이버 길찾기 정보를 불러오지 못했습니다.",
                    exception
            );
        }
    }

    DrivingRouteResponseDTO parseResponse(String responseBody) throws Exception {
        DirectionsApiResponse response = jsonMapper.readValue(
                responseBody,
                DirectionsApiResponse.class
        );
        if (response == null || response.code() != 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "현재 위치에서 세차장까지의 차량 경로를 찾을 수 없습니다."
            );
        }

        List<RouteOption> routes = response.route() == null
                ? null
                : response.route().traoptimal();
        if (routes == null || routes.isEmpty()) {
            throw invalidResponse();
        }

        RouteOption route = routes.get(0);
        if (route.summary() == null || route.path() == null || route.path().size() < 2) {
            throw invalidResponse();
        }

        List<DrivingRoutePointDTO> path = route.path().stream()
                .filter(point -> point != null
                        && point.size() >= 2
                        && point.get(0) != null
                        && point.get(1) != null
                        && Double.isFinite(point.get(0))
                        && Double.isFinite(point.get(1)))
                .map(point -> new DrivingRoutePointDTO(point.get(1), point.get(0)))
                .toList();
        if (path.size() < 2) {
            throw invalidResponse();
        }

        return new DrivingRouteResponseDTO(
                route.summary().distance(),
                route.summary().duration(),
                path
        );
    }

    private void validateConfigured() {
        if (clientId == null || clientId.isBlank()
                || clientSecret == null || clientSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "네이버 길찾기 API 인증키가 설정되지 않았습니다."
            );
        }
    }

    private void validateCoordinate(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < 33 || latitude > 39.5
                || longitude < 124 || longitude > 132) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "길찾기 좌표를 확인해주세요."
            );
        }
    }

    private ResponseStatusException invalidResponse() {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "네이버 길찾기 응답 형식이 올바르지 않습니다."
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DirectionsApiResponse(
            int code,
            String message,
            RouteContainer route
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RouteContainer(List<RouteOption> traoptimal) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RouteOption(
            RouteSummary summary,
            List<List<Double>> path
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RouteSummary(
            int distance,
            long duration
    ) {
    }
}
