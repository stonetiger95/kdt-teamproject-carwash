package kdt.washPrj.predictiveMaintenance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SimulationStatusDTO {
    private Long demoRunId;
    private String runStatus;
    private LocalDateTime currentCollectedAt;
    private Integer playbackIntervalMs;
    private Boolean telegramEnabled;
}
