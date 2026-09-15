package kdt.washPrj.predictiveMaintenance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PredictionRequestDTO {

    @JsonProperty("equipment_id")
    private String equipmentId;

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
}