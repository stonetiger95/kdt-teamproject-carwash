package kdt.washPrj.predictiveMaintenance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SensorReadingDTO {

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
}