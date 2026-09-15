package kdt.washPrj.predictiveMaintenance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PredictionResultDTO {

    @JsonProperty("equipment_id")
    private String equipmentId;

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
}