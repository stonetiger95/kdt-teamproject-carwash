package kdt.washPrj.predictiveMaintenance;

import kdt.washPrj.predictiveMaintenance.dto.AlertDTO;
import kdt.washPrj.predictiveMaintenance.dto.DashboardResponseDTO;
import kdt.washPrj.predictiveMaintenance.dto.EquipmentStatusDTO;
import kdt.washPrj.predictiveMaintenance.dto.SimulationStatusDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PredictiveMaintenanceSimulationService {

    private final PredictiveMaintenanceMapper mapper;
    private final PredictiveMaintenanceService predictiveMaintenanceService;

    public SimulationStatusDTO start(String loginId, boolean telegramEnabled) {
        return mapper.startDemo(loginId, telegramEnabled);
    }

    public SimulationStatusDTO stop(String loginId) {
        return mapper.stopDemo(loginId);
    }

    public SimulationStatusDTO pause(String loginId) {
        return mapper.pauseDemo(loginId);
    }

    public SimulationStatusDTO resume(String loginId) {
        return mapper.resumeDemo(loginId);
    }

    public SimulationStatusDTO status(String loginId) {
        return mapper.getDemoStatus(loginId);
    }

    public DashboardResponseDTO dashboard(String loginId) {
        SimulationStatusDTO status = status(loginId);
        if (status == null) {
            DashboardResponseDTO empty = new DashboardResponseDTO();
            empty.setMode("DEMO");
            empty.setEquipment(List.of());
            return empty;
        }

        DashboardResponseDTO dashboard = predictiveMaintenanceService.getDemoDashboard(
                loginId,
                status.getCurrentCollectedAt()
        );
        dashboard.setDemoRunId(status.getDemoRunId());
        dashboard.setSimulationRunning("RUNNING".equals(status.getRunStatus()));
        dashboard.setSimulationPaused("PAUSED".equals(status.getRunStatus()));
        dashboard.setTelegramEnabled(Boolean.TRUE.equals(status.getTelegramEnabled()));
        recordCurrentAlerts(loginId, status.getDemoRunId(), dashboard.getEquipment());

        Map<String, Long> counts = mapper.findDemoAlerts(loginId, status.getDemoRunId(), null)
                .stream().collect(Collectors.groupingBy(AlertDTO::getEquipmentId, Collectors.counting()));
        dashboard.getEquipment().forEach(item -> item.setWarningEventCount(
                counts.getOrDefault(item.getEquipmentId(), 0L).intValue()
        ));
        return dashboard;
    }

    public List<AlertDTO> alerts(String loginId, Long demoRunId, String equipmentId) {
        return mapper.findDemoAlerts(loginId, demoRunId, equipmentId);
    }

    public void advanceAll() {
        mapper.advanceDemoRuns();
    }

    private void recordCurrentAlerts(String loginId, Long demoRunId, List<EquipmentStatusDTO> equipment) {
        for (EquipmentStatusDTO item : equipment) {
            int rank = alertRank(item);
            if (rank == 0) continue;
            mapper.recordDemoAlert(
                    loginId,
                    demoRunId,
                    item.getEquipmentId(),
                    rank >= 2 ? "CRITICAL" : "WARNING",
                    item.getRiskScore(),
                    issueSummary(item),
                    item.getCollectedAt()
            );
        }
    }

    private int alertRank(EquipmentStatusDTO item) {
        double score = item.getRiskScore() == null ? 0 : item.getRiskScore();
        if ("CRITICAL".equals(item.getRiskLevel()) || score >= 80) return 2;
        if ("WARNING".equals(item.getRiskLevel()) || score >= 50) return 1;
        return 0;
    }

    private String issueSummary(EquipmentStatusDTO item) {
        if (item.getSuccessRate() != null && item.getSuccessRate() < 95) return "성공률 저하";
        if (item.getResponseTimeMs() != null && item.getResponseTimeMs() > 200) return "응답시간 증가";
        if (item.getPacketLossRate() != null && item.getPacketLossRate() > 1) return "패킷 손실 증가";
        if (item.getSignalStrengthDbm() != null && item.getSignalStrengthDbm() < -70) return "신호 세기 약함";
        return "위험도 상승";
    }
}
