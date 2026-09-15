package kdt.washPrj.predictiveMaintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import kdt.washPrj.predictiveMaintenance.dto.DashboardResponseDTO;
import kdt.washPrj.predictiveMaintenance.dto.EquipmentStatusDTO;
import kdt.washPrj.predictiveMaintenance.dto.PredictionRequestDTO;
import kdt.washPrj.predictiveMaintenance.dto.PredictionResultDTO;
import kdt.washPrj.predictiveMaintenance.dto.SensorReadingDTO;
import kdt.washPrj.predictiveMaintenance.dto.TelegramAlertRequestDTO;
import kdt.washPrj.predictiveMaintenance.dto.TelegramDeliveryResultDTO;
import kdt.washPrj.predictiveMaintenance.dto.V2ShadowPredictionRequestDTO;
import kdt.washPrj.predictiveMaintenance.dto.V2ShadowPredictionResultDTO;
import kdt.washPrj.predictiveMaintenance.dto.V2ShadowSensorReadingDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictiveMaintenanceService {

    private final PredictiveMaintenanceMapper mapper;
    private final PredictiveMaintenanceClient predictiveMaintenanceClient;
    private final TelegramNotificationClient telegramNotificationClient;
    private final PredictiveMaintenanceProperties properties;

    public DashboardResponseDTO getDashboard(String loginId) {
        return getDashboardAt(
                loginId,
                mapper.findFirstCollectedAt(loginId)
        );
    }

    public DashboardResponseDTO getLatestDashboard(String loginId) {
        LocalDateTime collectedAt = mapper.findLiveLatestCollectedAt(loginId);
        return buildDashboard(
                loginId,
                collectedAt,
                collectedAt == null
                        ? List.of()
                        : mapper.findLiveReadingsAt(loginId, collectedAt),
                "LIVE"
        );
    }

    public DashboardResponseDTO getDemoDashboard(String loginId, LocalDateTime collectedAt) {
        return buildDashboard(
                loginId,
                collectedAt,
                collectedAt == null
                        ? List.of()
                        : mapper.findDemoReadingsAt(loginId, collectedAt),
                "DEMO"
        );
    }

    public DashboardResponseDTO getNextDashboard(
            String loginId,
            LocalDateTime currentTime
    ) {
        LocalDateTime nextTime = mapper.findNextCollectedAt(
                loginId,
                currentTime
        );
        if (nextTime == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "다음 센서 데이터가 없습니다."
            );
        }
        return getDashboardAt(loginId, nextTime);
    }

    public DashboardResponseDTO getDashboardAtTime(
            String loginId,
            LocalDateTime collectedAt
    ) {
        return getDashboardAt(loginId, collectedAt);
    }

    private DashboardResponseDTO getDashboardAt(
            String loginId,
            LocalDateTime collectedAt
    ) {
        if (collectedAt == null) {
            DashboardResponseDTO empty = new DashboardResponseDTO();
            empty.setEquipment(List.of());
            return empty;
        }

        return buildDashboard(
                loginId,
                collectedAt,
                mapper.findReadingsAt(loginId, collectedAt),
                "DEMO"
        );
    }

    private DashboardResponseDTO buildDashboard(
            String loginId,
            LocalDateTime collectedAt,
            List<SensorReadingDTO> readings,
            String mode
    ) {
        if (collectedAt == null) {
            DashboardResponseDTO empty = new DashboardResponseDTO();
            empty.setMode(mode);
            empty.setEquipment(List.of());
            return empty;
        }
        List<EquipmentStatusDTO> equipment = readings
                .stream()
                .map(reading -> toEquipmentStatus(loginId, reading, mode))
                .collect(Collectors.toList());

        DashboardResponseDTO response = new DashboardResponseDTO();
        response.setMode(mode);
        response.setCollectedAt(collectedAt);
        response.setTotalEquipment(equipment.size());
        response.setNormalCount(countByRisk(equipment, "NORMAL"));
        response.setWarningCount(countByRisk(equipment, "WARNING"));
        response.setCriticalCount(countByRisk(equipment, "CRITICAL"));
        response.setEquipment(equipment);
        return response;
    }

    public PredictionResultDTO predict(PredictionRequestDTO request) {
        return predictiveMaintenanceClient.predict(request);
    }

    public void sendTelegramTest(String loginId) {
        String message = "[세차장 예지보전 테스트]\n"
                + "관리자 " + loginId + "의 텔레그램 연결이 정상입니다.";
        deliverTelegram(
                loginId,
                "test-" + UUID.randomUUID(),
                "TEST",
                null,
                message
        );
    }

    public TelegramDeliveryResultDTO sendTelegramAlerts(
            String loginId,
            List<TelegramAlertRequestDTO> alerts
    ) {
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        for (TelegramAlertRequestDTO alert : alerts) {
            String type = normalizeNotificationType(alert.getAlertLevel());
            try {
                DeliveryOutcome outcome = deliverTelegram(
                        loginId,
                        alert.getEventKey(),
                        type,
                        alert.getEquipmentId(),
                        buildAlertMessage(alert, type)
                );
                if (outcome == DeliveryOutcome.SUCCESS) {
                    successCount++;
                } else {
                    skippedCount++;
                }
            } catch (RuntimeException exception) {
                failedCount++;
            }
        }

        return new TelegramDeliveryResultDTO(
                alerts.size(),
                successCount,
                failedCount,
                skippedCount
        );
    }

    private DeliveryOutcome deliverTelegram(
            String loginId,
            String eventKey,
            String notificationType,
            String equipmentId,
            String message
    ) {
        String recipient = telegramNotificationClient.getRecipient();
        Long deliveryId = mapper.beginNotificationDelivery(
                loginId,
                eventKey,
                notificationType,
                equipmentId,
                recipient,
                message
        );
        if (deliveryId == null) {
            return DeliveryOutcome.SKIPPED;
        }

        try {
            telegramNotificationClient.sendMessage(message);
            mapper.completeNotificationDelivery(
                    loginId,
                    deliveryId,
                    true,
                    null
            );
            return DeliveryOutcome.SUCCESS;
        } catch (RuntimeException exception) {
            log.error(
                    "Telegram delivery failed or its history could not be completed. deliveryId={}, eventKey={}",
                    deliveryId,
                    eventKey,
                    exception
            );
            try {
                mapper.completeNotificationDelivery(
                        loginId,
                        deliveryId,
                        false,
                        safeDeliveryError(exception)
                );
            } catch (RuntimeException historyException) {
                log.error(
                        "Telegram failure history update failed. deliveryId={}",
                        deliveryId,
                        historyException
                );
            }
            throw exception;
        }
    }

    private String normalizeNotificationType(String alertLevel) {
        String type = alertLevel == null ? "" : alertLevel.trim().toUpperCase();
        if (!Set.of("WARNING", "CRITICAL", "RECOVERY").contains(type)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "지원하지 않는 텔레그램 알림 단계입니다."
            );
        }
        return type;
    }

    private String buildAlertMessage(
            TelegramAlertRequestDTO alert,
            String type
    ) {
        String levelLabel = "CRITICAL".equals(type) ? "긴급" : "주의";
        return "[예지보전 " + levelLabel + " 알림]\n"
                + "장비: " + alert.getEquipmentId() + "\n"
                + "위치: " + valueOrDefault(alert.getLocationId(), "미지정") + "\n"
                + "종류: " + valueOrDefault(alert.getEquipmentType(), "장비") + "\n"
                + "위험도: " + String.format("%.1f", alert.getRiskScore()) + " / 100\n"
                + "상태: " + valueOrDefault(alert.getAlertLabel(), levelLabel) + "\n"
                + "원인: " + valueOrDefault(alert.getIssueSummary(), "상세 확인 필요") + "\n"
                + "감지 시각: " + alert.getCollectedAt();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safeDeliveryError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private enum DeliveryOutcome {
        SUCCESS,
        SKIPPED
    }

    public List<EquipmentStatusDTO> getEquipmentHistory(
            String loginId,
            String equipmentId,
            LocalDateTime collectedAt,
            int requestedLimit
    ) {
        int limit = Math.max(1, Math.min(requestedLimit, 168));
        return mapper.findReadingsByEquipmentUntil(
                        loginId,
                        equipmentId,
                        collectedAt,
                        limit
                )
                .stream()
                .map(reading -> toEquipmentStatus(loginId, reading, "DEMO"))
                .sorted((left, right) -> left.getCollectedAt()
                        .compareTo(right.getCollectedAt()))
                .collect(Collectors.toList());
    }

    public List<EquipmentStatusDTO> getDemoEquipmentHistory(String loginId, String equipmentId,
                                                             LocalDateTime collectedAt, int limit) {
        return mapper.findDemoHistory(loginId, equipmentId, collectedAt, Math.max(1, Math.min(limit, 168)))
                .stream()
                .map(reading -> toEquipmentStatus(loginId, reading, "DEMO"))
                .collect(Collectors.toList());
    }

    public List<EquipmentStatusDTO> getLiveEquipmentHistory(String loginId, String equipmentId,
                                                             LocalDateTime collectedAt, int limit) {
        return mapper.findLiveHistory(loginId, equipmentId, collectedAt, Math.max(1, Math.min(limit, 168)))
                .stream()
                .map(reading -> toEquipmentStatus(loginId, reading, "LIVE"))
                .sorted((a,b) -> a.getCollectedAt().compareTo(b.getCollectedAt()))
                .collect(Collectors.toList());
    }

    /** Recomputes the active v2 score with a shadow-only response marker. */
    public V2ShadowPredictionResultDTO predictShadow(
            String loginId,
            String equipmentId,
            LocalDateTime collectedAt,
            String requestedMode
    ) {
        String mode = normalizeMode(requestedMode);
        List<SensorReadingDTO> history = loadV2History(
                loginId, equipmentId, collectedAt, mode
        );
        if (history == null || history.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "v2 모델에 전달할 센서 이력이 없습니다."
            );
        }
        V2ShadowPredictionResultDTO result =
                predictiveMaintenanceClient.predictShadow(
                        buildV2Request(equipmentId, history)
                );
        recordV2Prediction(loginId, mode, equipmentId, collectedAt, result);
        return result;
    }

    /**
     * Dashboard policy: v2 is primary. v1 is called only while v2 is warming
     * up, lacks a reviewed baseline, or the v2 service is unavailable.
     */
    private EquipmentStatusDTO toEquipmentStatus(
            String loginId,
            SensorReadingDTO reading,
            String mode
    ) {
        EquipmentStatusDTO status = baseStatus(reading);
        if (!"v2".equalsIgnoreCase(properties.getActiveModel())) {
            return applyV1(status, reading, "V1_ACTIVE", null, null);
        }

        try {
            List<SensorReadingDTO> history = loadV2History(
                    loginId,
                    reading.getEquipmentId(),
                    reading.getCollectedAt(),
                    mode
            );
            V2ShadowPredictionResultDTO v2 = predictiveMaintenanceClient
                    .predictV2(buildV2Request(reading.getEquipmentId(), history));
            recordV2Prediction(
                    loginId, mode, reading.getEquipmentId(),
                    reading.getCollectedAt(), v2
            );
            status.setV2Status(v2.getStatus());
            if ("SCORED".equals(v2.getStatus())) {
                status.setRiskScore(v2.getRiskScore());
                status.setRiskLevel(v2.getRiskLevel());
                status.setModelVersion(v2.getModelVersion());
                status.setPredictionSource("V2_ACTIVE");
                return status;
            }
            return fallbackOrUnavailable(
                    status, reading, v2.getStatus(), v2.getMessage()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "v2 primary failed; applying configured fallback. mode={}, equipmentId={}, collectedAt={}",
                    mode, reading.getEquipmentId(), reading.getCollectedAt(),
                    exception
            );
            return fallbackOrUnavailable(
                    status,
                    reading,
                    "FAILED",
                    "v2 호출 실패: " + safeDeliveryError(exception)
            );
        }
    }

    private EquipmentStatusDTO fallbackOrUnavailable(
            EquipmentStatusDTO status,
            SensorReadingDTO reading,
            String v2Status,
            String reason
    ) {
        if (!properties.isFallbackToV1()) {
            status.setModelVersion("logistic-v2-active");
            status.setPredictionSource("V2_UNAVAILABLE");
            status.setV2Status(v2Status);
            status.setFallbackReason(reason);
            return status;
        }
        return applyV1(status, reading, "V1_FALLBACK", v2Status, reason);
    }

    private EquipmentStatusDTO applyV1(
            EquipmentStatusDTO status,
            SensorReadingDTO reading,
            String source,
            String v2Status,
            String fallbackReason
    ) {
        PredictionResultDTO prediction = predictiveMaintenanceClient.predict(
                buildV1Request(reading)
        );
        status.setRiskScore(prediction.getRiskScore());
        status.setRiskLevel(prediction.getRiskLevel());
        status.setModelVersion(prediction.getModelVersion());
        status.setPredictionSource(source);
        status.setV2Status(v2Status);
        status.setFallbackReason(fallbackReason);
        return status;
    }

    private PredictionRequestDTO buildV1Request(SensorReadingDTO reading) {
        PredictionRequestDTO request = new PredictionRequestDTO();
        request.setEquipmentId(reading.getEquipmentId());
        request.setSignalStrengthDbm(reading.getSignalStrengthDbm());
        request.setSuccessRate(reading.getSuccessRate());
        request.setResponseTimeMs(reading.getResponseTimeMs());
        request.setRetryCount(reading.getRetryCount());
        request.setDisconnectCount(reading.getDisconnectCount());
        request.setPacketLossRate(reading.getPacketLossRate());
        request.setErrorCount(reading.getErrorCount());
        return request;
    }

    private EquipmentStatusDTO baseStatus(SensorReadingDTO reading) {
        EquipmentStatusDTO status = new EquipmentStatusDTO();
        status.setEquipmentId(reading.getEquipmentId());
        status.setLocationId(reading.getLocationId());
        status.setEquipmentType(reading.getEquipmentType());
        status.setCollectedAt(reading.getCollectedAt());
        status.setTemperatureC(reading.getTemperatureC());
        status.setVoltageV(reading.getVoltageV());
        status.setSignalStrengthDbm(reading.getSignalStrengthDbm());
        status.setSuccessRate(reading.getSuccessRate());
        status.setResponseTimeMs(reading.getResponseTimeMs());
        status.setPacketLossRate(reading.getPacketLossRate());
        return status;
    }

    private String normalizeMode(String requestedMode) {
        String mode = requestedMode == null
                ? "LIVE"
                : requestedMode.trim().toUpperCase();
        if (!Set.of("LIVE", "DEMO").contains(mode)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "지원하지 않는 예지보전 데이터 모드입니다."
            );
        }
        return mode;
    }

    private List<SensorReadingDTO> loadV2History(
            String loginId,
            String equipmentId,
            LocalDateTime collectedAt,
            String requestedMode
    ) {
        String mode = normalizeMode(requestedMode);
        List<SensorReadingDTO> history = "DEMO".equals(mode)
                ? mapper.findDemoHistory(loginId, equipmentId, collectedAt, 168)
                : mapper.findLiveHistory(loginId, equipmentId, collectedAt, 168);
        return history == null ? List.of() : history;
    }

    private V2ShadowPredictionRequestDTO buildV2Request(
            String equipmentId,
            List<SensorReadingDTO> history
    ) {
        List<V2ShadowSensorReadingDTO> readings = history.stream()
                .filter(row -> equipmentId.equals(row.getEquipmentId()))
                .sorted((left, right) -> left.getCollectedAt()
                        .compareTo(right.getCollectedAt()))
                .map(this::toV2ShadowReading)
                .toList();
        V2ShadowPredictionRequestDTO request =
                new V2ShadowPredictionRequestDTO();
        request.setEquipmentId(equipmentId);
        request.setReadings(readings);
        return request;
    }

    private V2ShadowSensorReadingDTO toV2ShadowReading(
            SensorReadingDTO source
    ) {
        V2ShadowSensorReadingDTO target = new V2ShadowSensorReadingDTO();
        target.setEquipmentId(source.getEquipmentId());
        target.setCollectedAt(source.getCollectedAt());
        target.setTrafficLoad(source.getTrafficLoad());
        target.setTemperatureC(source.getTemperatureC());
        target.setVoltageV(source.getVoltageV());
        target.setSignalStrengthDbm(source.getSignalStrengthDbm());
        target.setSuccessRate(source.getSuccessRate());
        target.setResponseTimeMs(source.getResponseTimeMs());
        target.setRetryCount(source.getRetryCount());
        target.setDisconnectCount(source.getDisconnectCount());
        target.setPacketLossRate(source.getPacketLossRate());
        target.setErrorCount(source.getErrorCount());
        target.setDaysSinceMaintenance(source.getDaysSinceMaintenance());
        return target;
    }

    private void recordV2Prediction(
            String loginId,
            String mode,
            String equipmentId,
            LocalDateTime collectedAt,
            V2ShadowPredictionResultDTO result
    ) {
        try {
            mapper.recordShadowPrediction(
                    loginId, mode, equipmentId, collectedAt,
                    result.getModelVersion(), result.getStatus(),
                    result.getRawProbability(),
                    result.getCalibratedProbability(),
                    result.getRiskScore(), result.getRiskLevel(),
                    result.getHistoryRows(), result.getMessage()
            );
        } catch (RuntimeException exception) {
            // Audit persistence must never hide an otherwise valid v2 score.
            log.warn(
                    "v2 result persistence skipped. mode={}, equipmentId={}, collectedAt={}",
                    mode, equipmentId, collectedAt, exception
            );
        }
    }

    public List<kdt.washPrj.predictiveMaintenance.dto.AlertDTO> getLiveAlerts(
            String loginId,
            String equipmentId
    ) {
        // 운영 센서와 실제 장비의 매핑 기준이 확정되기 전에는
        // 임의 키로 경고 이력을 연결하지 않는다.
        return List.of();
    }

    private int countByRisk(List<EquipmentStatusDTO> equipment, String risk) {
        return (int) equipment.stream()
                .map(EquipmentStatusDTO::getRiskLevel)
                .filter(Objects::nonNull)
                .filter(risk::equals)
                .count();
    }
}
