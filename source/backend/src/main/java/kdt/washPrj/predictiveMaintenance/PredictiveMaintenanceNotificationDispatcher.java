package kdt.washPrj.predictiveMaintenance;

import kdt.washPrj.predictiveMaintenance.dto.TelegramAlertRequestDTO;
import kdt.washPrj.predictiveMaintenance.dto.TelegramDeliveryResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictiveMaintenanceNotificationDispatcher {

    private final PredictiveMaintenanceService service;

    @Async("predictiveMaintenanceNotificationExecutor")
    public void dispatchTelegram(String loginId, List<TelegramAlertRequestDTO> alerts) {
        try {
            TelegramDeliveryResultDTO result = service.sendTelegramAlerts(loginId, List.copyOf(alerts));
            log.info(
                    "Predictive-maintenance Telegram dispatch completed. requested={}, success={}, failed={}, skipped={}",
                    result.getRequestedCount(),
                    result.getSuccessCount(),
                    result.getFailedCount(),
                    result.getSkippedCount()
            );
        } catch (RuntimeException exception) {
            log.error("Predictive-maintenance Telegram dispatch failed. loginId={}", loginId, exception);
        }
    }
}
