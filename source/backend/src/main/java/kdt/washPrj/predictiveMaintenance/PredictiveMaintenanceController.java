package kdt.washPrj.predictiveMaintenance;

import lombok.RequiredArgsConstructor;
import kdt.washPrj.predictiveMaintenance.dto.DashboardResponseDTO;
import kdt.washPrj.predictiveMaintenance.dto.PredictionRequestDTO;
import kdt.washPrj.predictiveMaintenance.dto.PredictionResultDTO;
import kdt.washPrj.predictiveMaintenance.dto.TelegramAlertBatchRequestDTO;
import kdt.washPrj.predictiveMaintenance.dto.AlertDTO;
import kdt.washPrj.predictiveMaintenance.dto.SimulationStartRequestDTO;
import kdt.washPrj.predictiveMaintenance.dto.SimulationStatusDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import kdt.washPrj.predictiveMaintenance.dto.EquipmentStatusDTO;
import kdt.washPrj.predictiveMaintenance.dto.V2ShadowPredictionResultDTO;

import java.util.List;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/predictive-maintenance")
@RequiredArgsConstructor
public class PredictiveMaintenanceController {

    private final PredictiveMaintenanceService service;
    private final PredictiveMaintenanceSimulationService simulationService;
    private final PredictiveMaintenanceNotificationDispatcher notificationDispatcher;

    @PostMapping("/telegram/test")
    public void sendTelegramTest(Authentication authentication) {
        service.sendTelegramTest(authentication.getName());
    }

    @PostMapping("/telegram/alerts")
    public ResponseEntity<Void> sendTelegramAlerts(
            Authentication authentication,
            @Valid @RequestBody TelegramAlertBatchRequestDTO request
    ) {
        notificationDispatcher.dispatchTelegram(authentication.getName(), request.getAlerts());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/dashboard")
    public DashboardResponseDTO dashboard(Authentication authentication) {
        return service.getDashboard(authentication.getName());
    }

    @GetMapping("/dashboard/latest")
    public DashboardResponseDTO latestDashboard(Authentication authentication) {
        return service.getLatestDashboard(authentication.getName());
    }

    @GetMapping("/dashboard/mode")
    public DashboardResponseDTO dashboardByMode(Authentication authentication,
                                                @RequestParam(defaultValue = "LIVE") String mode) {
        if ("DEMO".equalsIgnoreCase(mode)) {
            return simulationService.dashboard(authentication.getName());
        }
        return service.getLatestDashboard(authentication.getName());
    }

    @GetMapping("/dashboard/live")
    public DashboardResponseDTO liveDashboard(Authentication authentication) {
        return simulationService.dashboard(authentication.getName());
    }

    @PostMapping("/dashboard/live/restart")
    public DashboardResponseDTO restartLiveDashboard(Authentication authentication) {
        String loginId = authentication.getName();
        simulationService.start(loginId, false);
        return simulationService.dashboard(loginId);
    }

    @PostMapping("/dashboard/live/stop")
    public DashboardResponseDTO stopLiveDashboard(Authentication authentication) {
        String loginId = authentication.getName();
        simulationService.stop(loginId);
        return simulationService.dashboard(loginId);
    }

    @PostMapping("/demo/start")
    public SimulationStatusDTO startDemo(Authentication authentication,
                                         @RequestBody(required = false) SimulationStartRequestDTO request) {
        return simulationService.start(authentication.getName(), request != null && request.isTelegramEnabled());
    }

    @PostMapping("/demo/stop")
    public SimulationStatusDTO stopDemo(Authentication authentication) {
        return simulationService.stop(authentication.getName());
    }

    @PostMapping("/demo/pause")
    public SimulationStatusDTO pauseDemo(Authentication authentication) {
        return simulationService.pause(authentication.getName());
    }

    @PostMapping("/demo/resume")
    public SimulationStatusDTO resumeDemo(Authentication authentication) {
        return simulationService.resume(authentication.getName());
    }

    @GetMapping("/demo/status")
    public SimulationStatusDTO demoStatus(Authentication authentication) {
        return simulationService.status(authentication.getName());
    }

    @GetMapping("/demo/{demoRunId}/alerts")
    public List<AlertDTO> demoAlerts(Authentication authentication, @PathVariable Long demoRunId,
                                    @RequestParam(required = false) String equipmentId) {
        return simulationService.alerts(authentication.getName(), demoRunId, equipmentId);
    }

    @GetMapping("/dashboard/next")
    public DashboardResponseDTO nextDashboard(
            Authentication authentication,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime currentTime
    ) {
        return service.getNextDashboard(authentication.getName(), currentTime);
    }

    @GetMapping("/dashboard/at")
    public DashboardResponseDTO dashboardAt(
            Authentication authentication,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime collectedAt
    ) {
        return service.getDashboardAtTime(
                authentication.getName(),
                collectedAt
        );
    }

    @PostMapping(value = "/predict", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PredictionResultDTO predict(
            @Valid @RequestBody PredictionRequestDTO request
    ) {
        return service.predict(request);
    }

    @GetMapping("/equipment/{equipmentId}/history")
    public List<EquipmentStatusDTO> equipmentHistory(
            Authentication authentication,
            @PathVariable String equipmentId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime collectedAt,
            @RequestParam(defaultValue = "24") int limit,
            @RequestParam(defaultValue = "LIVE") String mode
    ) {
        if ("DEMO".equalsIgnoreCase(mode)) {
            return service.getDemoEquipmentHistory(authentication.getName(), equipmentId, collectedAt, limit);
        }
        return service.getLiveEquipmentHistory(
                authentication.getName(),
                equipmentId,
                collectedAt,
                limit
        );
    }

    @GetMapping("/equipment/{equipmentId}/alerts")
    public List<AlertDTO> equipmentAlerts(Authentication authentication, @PathVariable String equipmentId,
                                          @RequestParam(defaultValue = "LIVE") String mode,
                                          @RequestParam(required = false) Long demoRunId) {
        if ("DEMO".equalsIgnoreCase(mode)) {
            return demoRunId == null ? List.of() : simulationService.alerts(authentication.getName(), demoRunId, equipmentId);
        }
        return service.getLiveAlerts(authentication.getName(), equipmentId);
    }

    @GetMapping("/equipment/{equipmentId}/shadow")
    public V2ShadowPredictionResultDTO equipmentShadow(
            Authentication authentication,
            @PathVariable String equipmentId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime collectedAt,
            @RequestParam(defaultValue = "LIVE") String mode
    ) {
        return service.predictShadow(
                authentication.getName(),
                equipmentId,
                collectedAt,
                mode
        );
    }
}
