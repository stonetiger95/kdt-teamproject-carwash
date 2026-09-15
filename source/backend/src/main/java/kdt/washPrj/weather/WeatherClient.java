package kdt.washPrj.weather;

import kdt.washPrj.weather.dto.KmaWeatherResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class WeatherClient {

    private final WeatherProperties properties;
    private final RestClient restClient;

    public WeatherClient(
            WeatherProperties properties,
            RestClient restClient
    ) {
        this.properties = properties;
        this.restClient = restClient.mutate()
                .baseUrl(properties.getApiUrl())
                .build();
    }

    public KmaWeatherResponseDTO getForecast(
            String baseDate,
            String baseTime,
            int nx,
            int ny
    ) {
        validateProperties();

        URI requestUri = UriComponentsBuilder
                .fromUriString(properties.getApiUrl())
                .path("/getVilageFcst")
                .queryParam("serviceKey", properties.getServiceKey())
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 1000)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", nx)
                .queryParam("ny", ny)
                .build(true)
                .toUri();

        try {
            KmaWeatherResponseDTO result = restClient.get()
                    .uri(requestUri)
                    .retrieve()
                    .body(KmaWeatherResponseDTO.class);

            validateResponse(result);
            return result;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "기상청 날씨 정보를 불러오지 못했습니다.",
                    exception
            );
        }
    }

    private void validateProperties() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "기상청 API가 비활성화되어 있습니다."
            );
        }

        if (properties.getServiceKey() == null
                || properties.getServiceKey().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "기상청 API 인증키가 설정되지 않았습니다."
            );
        }
    }

    private void validateResponse(KmaWeatherResponseDTO result) {
        if (result == null
                || result.getResponse() == null
                || result.getResponse().getHeader() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "기상청 응답 형식이 올바르지 않습니다."
            );
        }

        String resultCode =
                result.getResponse().getHeader().getResultCode();

        if (!"00".equals(resultCode) && !"0".equals(resultCode)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "기상청 API 요청에 실패했습니다."
            );
        }
    }
}