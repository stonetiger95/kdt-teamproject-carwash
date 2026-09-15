package kdt.washPrj.predictiveMaintenance;

import kdt.washPrj.predictiveMaintenance.dto.PredictiveMaintenanceLogPageDTO;
import kdt.washPrj.predictiveMaintenance.dto.V2ShadowPredictionResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** Administrator API used by the SQL-free PM sensor-log audit page. */
@RestController
@RequestMapping("/api/admin/predictive-maintenance/logs")
@RequiredArgsConstructor
public class PredictiveMaintenanceLogController {
    private final PredictiveMaintenanceLogService service;

    @GetMapping
    public PredictiveMaintenanceLogPageDTO logs(
            Authentication authentication,
            @RequestParam(defaultValue = "LIVE") String mode,
            @RequestParam(required = false) String equipmentId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime fromAt,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime toAt,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return service.getLogs(
                authentication.getName(), mode, equipmentId, fromAt, toAt,
                riskLevel, page, size
        );
    }

    @GetMapping("/shadow")
    public V2ShadowPredictionResultDTO shadow(
            Authentication authentication,
            @RequestParam(defaultValue = "LIVE") String mode,
            @RequestParam String equipmentId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime collectedAt
    ) {
        return service.getShadowPrediction(
                authentication.getName(), mode, equipmentId, collectedAt
        );
    }
}
