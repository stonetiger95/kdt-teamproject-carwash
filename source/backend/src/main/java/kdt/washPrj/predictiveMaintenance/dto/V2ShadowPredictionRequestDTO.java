package kdt.washPrj.predictiveMaintenance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** v2 후보 모델 전용 요청. 이 요청은 운영 v1 판정을 대체하지 않는다. */
@Data
public class V2ShadowPredictionRequestDTO {

    @JsonProperty("equipment_id")
    private String equipmentId;

    private List<V2ShadowSensorReadingDTO> readings;
}
