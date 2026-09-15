package kdt.washPrj.predictiveMaintenance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlertDTO {
    private Long demoAlertId;
    private Long demoRunId;
    private String equipmentId;
    private String alertLevel;
    private Double riskScore;
    private String issueSummary;
    private LocalDateTime occurredAt;
}
