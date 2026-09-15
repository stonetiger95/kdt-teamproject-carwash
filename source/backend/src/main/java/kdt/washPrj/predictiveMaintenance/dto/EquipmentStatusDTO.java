package kdt.washPrj.predictiveMaintenance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EquipmentStatusDTO {
    private String equipmentId;
    private String locationId;
    private String equipmentType;
    private LocalDateTime collectedAt;
    private Double temperatureC;
    private Double voltageV;
    private Double signalStrengthDbm;
    private Double successRate;
    private Double responseTimeMs;
    private Double packetLossRate;
    private Double riskScore;
    private String riskLevel;
    private String modelVersion;
    /** V2_ACTIVE, V1_FALLBACK, V1_ACTIVE, or V2_UNAVAILABLE. */
    private String predictionSource;
    /** v2 input readiness/result status such as SCORED or WARMING_UP. */
    private String v2Status;
    /** Human-readable reason when the primary v2 decision falls back to v1. */
    private String fallbackReason;
    private int warningEventCount;
}
