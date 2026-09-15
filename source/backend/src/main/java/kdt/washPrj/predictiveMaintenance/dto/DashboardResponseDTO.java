package kdt.washPrj.predictiveMaintenance.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DashboardResponseDTO {
    private String mode;
    private Long demoRunId;
    private boolean simulationRunning;
    private boolean simulationPaused;
    private boolean telegramEnabled;
    private LocalDateTime collectedAt;
    private int totalEquipment;
    private int normalCount;
    private int warningCount;
    private int criticalCount;
    private List<EquipmentStatusDTO> equipment;
}
