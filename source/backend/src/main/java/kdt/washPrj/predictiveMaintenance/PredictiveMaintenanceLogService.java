package kdt.washPrj.predictiveMaintenance;

import kdt.washPrj.predictiveMaintenance.dto.PredictiveMaintenanceLogPageDTO;
import kdt.washPrj.predictiveMaintenance.dto.PredictiveMaintenanceLogRowDTO;
import kdt.washPrj.predictiveMaintenance.dto.V2ShadowPredictionResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Query normalization and bounded pagination for the PM audit-log page. */
@Service
@RequiredArgsConstructor
public class PredictiveMaintenanceLogService {
    private static final Set<String> MODES = Set.of("LIVE", "DEMO");
    private static final Set<String> RISK_LEVELS =
            Set.of("NORMAL", "WARNING", "CRITICAL");

    private final PredictiveMaintenanceLogMapper mapper;

    public PredictiveMaintenanceLogPageDTO getLogs(
            String loginId,
            String requestedMode,
            String equipmentId,
            LocalDateTime fromAt,
            LocalDateTime toAt,
            String requestedRiskLevel,
            int requestedPage,
            int requestedSize
    ) {
        String mode = normalizeRequired(requestedMode, "LIVE");
        if (!MODES.contains(mode)) {
            throw badRequest("mode는 LIVE 또는 DEMO여야 합니다.");
        }
        String riskLevel = normalizeOptional(requestedRiskLevel);
        if (riskLevel != null && !RISK_LEVELS.contains(riskLevel)) {
            throw badRequest("riskLevel은 NORMAL, WARNING, CRITICAL 중 하나여야 합니다.");
        }
        if (fromAt != null && toAt != null && fromAt.isAfter(toAt)) {
            throw badRequest("조회 시작 시각은 종료 시각보다 늦을 수 없습니다.");
        }

        int page = Math.max(0, requestedPage);
        int size = Math.max(10, Math.min(requestedSize, 100));
        long rawOffset = (long) page * size;
        int offset = (int) Math.min(rawOffset, Integer.MAX_VALUE);
        String normalizedEquipment = blankToNull(equipmentId);
        long total = mapper.countLogs(
                loginId, mode, normalizedEquipment, fromAt, toAt, riskLevel
        );
        List<PredictiveMaintenanceLogRowDTO> content = total == 0
                ? List.of()
                : mapper.findLogs(
                        loginId, mode, normalizedEquipment, fromAt, toAt,
                        riskLevel, offset, size
                );
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PredictiveMaintenanceLogPageDTO(
                content, page, size, total, totalPages
        );
    }

    public V2ShadowPredictionResultDTO getShadowPrediction(
            String loginId,
            String requestedMode,
            String equipmentId,
            LocalDateTime collectedAt
    ) {
        String mode = normalizeRequired(requestedMode, "LIVE");
        if (!MODES.contains(mode)) {
            throw badRequest("mode는 LIVE 또는 DEMO여야 합니다.");
        }
        if (blankToNull(equipmentId) == null || collectedAt == null) {
            throw badRequest("장비 ID와 수집 시각이 필요합니다.");
        }
        return mapper.findShadowPrediction(
                loginId, mode, equipmentId.trim(), collectedAt
        );
    }

    private String normalizeRequired(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        String normalized = blankToNull(value);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) return null;
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
