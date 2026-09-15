package kdt.washPrj.predictiveMaintenance;

import kdt.washPrj.predictiveMaintenance.dto.PredictionRequestDTO;
import kdt.washPrj.predictiveMaintenance.dto.PredictionResultDTO;
import kdt.washPrj.predictiveMaintenance.dto.V2ShadowPredictionRequestDTO;
import kdt.washPrj.predictiveMaintenance.dto.V2ShadowPredictionResultDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PredictiveMaintenanceClient {

    private final PredictiveMaintenanceProperties properties;
    private final RestClient restClient;

    public PredictiveMaintenanceClient(
            PredictiveMaintenanceProperties properties,
            RestClient restClient
    ) {
        this.properties = properties;
        this.restClient = restClient.mutate()
                .baseUrl(properties.getApiUrl())
                .build();
    }

    public PredictionResultDTO predict(
            PredictionRequestDTO request
    ) {
        validateProperties();

        try {
            PredictionResultDTO result = restClient.post()
                    .uri(properties.getPredictPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PredictionResultDTO.class);

            if (result == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "예지보전 API가 빈 응답을 반환했습니다."
                );
            }

            return result;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "예지보전 API 호출에 실패했습니다.",
                    exception
            );
        }
    }

    /** Calls the approved v2 primary path. A valid response must not be shadow-only. */
    public V2ShadowPredictionResultDTO predictV2(
            V2ShadowPredictionRequestDTO request
    ) {
        validateProperties();
        if (properties.getV2PredictPath() == null
                || properties.getV2PredictPath().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "예지보전 v2 활성 예측 경로가 설정되지 않았습니다."
            );
        }
        try {
            V2ShadowPredictionResultDTO result = restClient.post()
                    .uri(properties.getV2PredictPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(V2ShadowPredictionResultDTO.class);
            if (result == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "예지보전 v2 활성 API가 빈 응답을 반환했습니다."
                );
            }
            if (result.isShadowOnly()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "v2 활성 경로가 shadow-only 응답을 반환했습니다."
                );
            }
            return result;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "예지보전 v2 활성 API 호출에 실패했습니다.",
                    exception
            );
        }
    }

    /**
     * v2 비교 경로를 별도로 호출한다. 활성 판정과 같은 특징을 쓰지만 응답은
     * shadow_only=true이므로 운영 알림에 직접 사용할 수 없다.
     */
    public V2ShadowPredictionResultDTO predictShadow(
            V2ShadowPredictionRequestDTO request
    ) {
        validateProperties();
        if (properties.getShadowPredictPath() == null
                || properties.getShadowPredictPath().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "예지보전 v2 shadow 예측 경로가 설정되지 않았습니다."
            );
        }

        try {
            V2ShadowPredictionResultDTO result = restClient.post()
                    .uri(properties.getShadowPredictPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(V2ShadowPredictionResultDTO.class);

            if (result == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "예지보전 v2 shadow API가 빈 응답을 반환했습니다."
                );
            }
            if (!result.isShadowOnly()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "후보 모델 응답의 shadow_only 안전 표지가 없습니다."
                );
            }
            return result;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "예지보전 v2 shadow API 호출에 실패했습니다.",
                    exception
            );
        }
    }

    private void validateProperties() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "예지보전 API가 비활성화되어 있습니다."
            );
        }

        if (properties.getApiUrl() == null
                || properties.getApiUrl().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "예지보전 API 주소가 설정되지 않았습니다."
            );
        }

        if (properties.getPredictPath() == null
                || properties.getPredictPath().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "예지보전 예측 경로가 설정되지 않았습니다."
            );
        }
    }
}
