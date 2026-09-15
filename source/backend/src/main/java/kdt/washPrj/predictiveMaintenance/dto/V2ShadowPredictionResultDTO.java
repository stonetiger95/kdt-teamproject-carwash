package kdt.washPrj.predictiveMaintenance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** v2 result shared by active and observation-only endpoints. */
@Data
public class V2ShadowPredictionResultDTO {

    @JsonProperty("equipment_id")
    private String equipmentId;

    private String status;

    @JsonProperty("raw_probability")
    private Double rawProbability;

    @JsonProperty("calibrated_probability")
    private Double calibratedProbability;

    @JsonProperty("risk_score")
    private Double riskScore;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("model_version")
    private String modelVersion;

    @JsonProperty("shadow_only")
    private boolean shadowOnly;

    @JsonProperty("feature_count")
    private Integer featureCount;

    @JsonProperty("history_rows")
    private Integer historyRows;

    private String message;

    @JsonProperty("predicted_at")
    private LocalDateTime predictedAt;
}
