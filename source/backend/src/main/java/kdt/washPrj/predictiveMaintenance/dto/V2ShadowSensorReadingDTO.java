package kdt.washPrj.predictiveMaintenance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v2 후보 모델이 시간 특징을 재구성할 때 사용하는 원시 시간별 센서 행이다.
 * 운영 v1 DTO와 분리해 후보 모델 계약 변경이 운영 판정에 전파되지 않게 한다.
 */
@Data
public class V2ShadowSensorReadingDTO {

    @JsonProperty("equipment_id")
    private String equipmentId;

    @JsonProperty("collected_at")
    private LocalDateTime collectedAt;

    @JsonProperty("traffic_load")
    private Double trafficLoad;

    @JsonProperty("temperature_c")
    private Double temperatureC;

    @JsonProperty("voltage_v")
    private Double voltageV;

    @JsonProperty("signal_strength_dbm")
    private Double signalStrengthDbm;

    @JsonProperty("success_rate")
    private Double successRate;

    @JsonProperty("response_time_ms")
    private Double responseTimeMs;

    @JsonProperty("retry_count")
    private Integer retryCount;

    @JsonProperty("disconnect_count")
    private Integer disconnectCount;

    @JsonProperty("packet_loss_rate")
    private Double packetLossRate;

    @JsonProperty("error_count")
    private Integer errorCount;

    @JsonProperty("days_since_maintenance")
    private Integer daysSinceMaintenance;
}
