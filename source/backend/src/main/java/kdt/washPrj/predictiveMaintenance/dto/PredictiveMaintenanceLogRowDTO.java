package kdt.washPrj.predictiveMaintenance.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * One auditable sensor log row.
 *
 * Raw sensor values and persisted model output deliberately live in the same
 * DTO so an administrator can explain a prediction without opening SQL.
 */
@Data
public class PredictiveMaintenanceLogRowDTO {
    private String logKey;
    private String sourceMode;
    private Long readingId;
    private String equipmentId;
    private String locationId;
    private String equipmentType;
    private LocalDateTime collectedAt;

    private Integer dayIndex;
    private Integer hour;
    private Double trafficLoad;
    private Double temperatureC;
    private Double voltageV;
    private Double signalStrengthDbm;
    private Double successRate;
    private Double responseTimeMs;
    private Integer retryCount;
    private Integer disconnectCount;
    private Double packetLossRate;
    private Integer errorCount;
    private Integer daysSinceMaintenance;

    private Double rawProbability;
    private Double calibratedProbability;
    private Double riskScore;
    private String riskLevel;
    private String modelVersion;
    private LocalDateTime predictedAt;

    // DEMO-only ground-truth columns. LIVE rows intentionally return null.
    private Integer failureEvent;
    private Integer maintenanceEvent;
    private Integer failureWithin7d;
    private Double rulHours;
}

