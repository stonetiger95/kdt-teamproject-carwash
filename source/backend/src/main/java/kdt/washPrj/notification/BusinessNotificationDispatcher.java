package kdt.washPrj.notification;

import kdt.washPrj.notification.dto.BusinessNotificationEventDTO;
import kdt.washPrj.notification.dto.BusinessNotificationTargetDTO;
import kdt.washPrj.predictiveMaintenance.TelegramNotificationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class BusinessNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(
            BusinessNotificationDispatcher.class
    );
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BusinessNotificationMapper mapper;
    private final BusinessNotificationProperties properties;
    private final LineNotificationClient lineClient;
    private final TelegramNotificationClient telegramClient;

    public BusinessNotificationDispatcher(
            BusinessNotificationMapper mapper,
            BusinessNotificationProperties properties,
            LineNotificationClient lineClient,
            TelegramNotificationClient telegramClient
    ) {
        this.mapper = mapper;
        this.properties = properties;
        this.lineClient = lineClient;
        this.telegramClient = telegramClient;
    }

    @Scheduled(
            fixedDelayString = "${business-notification.dispatcher.fixed-delay-ms:5000}",
            initialDelayString = "${business-notification.dispatcher.initial-delay-ms:15000}"
    )
    public void dispatchPendingEvents() {
        if (!properties.isEnabled()) {
            return;
        }

        List<BusinessNotificationEventDTO> events = mapper.claimEvents(
                Math.max(properties.getBatchSize(), 1),
                Math.max(properties.getLeaseSeconds(), 30)
        );
        for (BusinessNotificationEventDTO event : events) {
            dispatchEvent(event);
        }
    }

    void dispatchEvent(BusinessNotificationEventDTO event) {
        List<BusinessNotificationTargetDTO> targets;
        try {
            targets = mapper.selectTargets(
                    event.getEventType(),
                    event.getEntityId()
            );
        } catch (RuntimeException exception) {
            finishWithFailure(event, exception);
            return;
        }

        if (targets == null || targets.isEmpty()) {
            mapper.completeEvent(event.getEventId(), "SKIPPED", null, 0);
            return;
        }

        int failed = 0;
        String lastError = null;
        for (BusinessNotificationTargetDTO target : targets) {
            String recipient = resolveRecipient(target);
            String message = buildMessage(event.getEventType(), target);
            Long deliveryId = null;
            try {
                deliveryId = mapper.beginDelivery(
                        event.getEventId(),
                        target.getChannel(),
                        recipient,
                        target.getTargetLoginId(),
                        target.getTargetRole(),
                        message
                );
                if (deliveryId == null) {
                    continue;
                }
                send(target.getChannel(), recipient, message);
                mapper.completeDelivery(deliveryId, "SENT", null);
            } catch (RuntimeException exception) {
                failed++;
                lastError = safeError(exception);
                log.warn(
                        "Business notification failed. eventId={}, type={}, channel={}, target={}",
                        event.getEventId(),
                        event.getEventType(),
                        target.getChannel(),
                        target.getTargetLoginId(),
                        exception
                );
                if (deliveryId != null) {
                    mapper.completeDelivery(deliveryId, "FAILED", lastError);
                }
            }
        }

        if (failed == 0) {
            mapper.completeEvent(event.getEventId(), "SENT", null, 0);
            return;
        }

        int attempts = event.getAttempts() == null ? 1 : event.getAttempts();
        String status = attempts >= Math.max(properties.getMaxAttempts(), 1)
                ? "FAILED"
                : "RETRY";
        mapper.completeEvent(
                event.getEventId(),
                status,
                lastError,
                Math.max(properties.getRetrySeconds(), 10)
        );
    }

    private void finishWithFailure(
            BusinessNotificationEventDTO event,
            RuntimeException exception
    ) {
        int attempts = event.getAttempts() == null ? 1 : event.getAttempts();
        mapper.completeEvent(
                event.getEventId(),
                attempts >= Math.max(properties.getMaxAttempts(), 1)
                        ? "FAILED"
                        : "RETRY",
                safeError(exception),
                Math.max(properties.getRetrySeconds(), 10)
        );
    }

    private String resolveRecipient(BusinessNotificationTargetDTO target) {
        if ("TELEGRAM".equals(target.getChannel())) {
            return telegramClient.getRecipient();
        }
        return target.getRecipient();
    }

    private void send(String channel, String recipient, String message) {
        if ("LINE".equals(channel)) {
            lineClient.sendMessage(recipient, message);
            return;
        }
        if ("TELEGRAM".equals(channel)) {
            telegramClient.sendMessage(message);
            return;
        }
        throw new IllegalArgumentException("지원하지 않는 알림 채널입니다.");
    }

    private String buildMessage(
            String eventType,
            BusinessNotificationTargetDTO target
    ) {
        return switch (eventType) {
            case "RESERVATION_CREATED" -> reservationCreated(target);
            case "RESERVATION_CANCELED" -> reservationCanceled(target);
            case "PAYMENT_SUCCEEDED" -> paymentSucceeded(target);
            case "INQUIRY_CREATED" -> inquiryCreated(target);
            case "INQUIRY_ANSWERED" -> inquiryAnswered(target);
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 비즈니스 알림 유형입니다."
            );
        };
    }

    private String reservationCreated(BusinessNotificationTargetDTO target) {
        String heading = "OWNER".equals(target.getTargetRole())
                ? "[새 예약]"
                : "[예약 완료]";
        return heading + "\n"
                + value(target.getSiteName(), "세차장") + "\n"
                + formatTime(target) + "\n"
                + "차량: " + value(target.getPlateNumber(), "확인 필요");
    }

    private String reservationCanceled(BusinessNotificationTargetDTO target) {
        return "[예약 취소]\n"
                + value(target.getSiteName(), "세차장") + "\n"
                + formatTime(target) + "\n"
                + "차량: " + value(target.getPlateNumber(), "확인 필요") + "\n"
                + "사유: " + value(target.getCancelReason(), "미입력");
    }

    private String paymentSucceeded(BusinessNotificationTargetDTO target) {
        return "[결제 완료]\n"
                + value(target.getSiteName(), "세차 서비스") + "\n"
                + "결제 금액: "
                + String.format("%,d원", target.getAmount() == null ? 0 : target.getAmount());
    }

    private String inquiryCreated(BusinessNotificationTargetDTO target) {
        String heading = "TELEGRAM".equals(target.getChannel())
                ? "[업주 문의 등록]"
                : "[고객 문의 등록]";
        return heading + "\n"
                + "제목: " + value(target.getTitle(), "제목 없음")
                + (target.getSiteName() == null
                ? ""
                : "\n세차장: " + target.getSiteName());
    }

    private String inquiryAnswered(BusinessNotificationTargetDTO target) {
        return "[문의 답변 등록]\n"
                + "제목: " + value(target.getTitle(), "제목 없음") + "\n"
                + "답변자: " + roleName(target.getActorRole())
                + "\n사이트에서 답변 내용을 확인해 주세요.";
    }

    private String formatTime(BusinessNotificationTargetDTO target) {
        return target.getReservationStartTime() == null
                ? "예약 시간: 확인 필요"
                : "예약 시간: " + target.getReservationStartTime().format(DATE_TIME);
    }

    private String roleName(String role) {
        return switch (value(role, "")) {
            case "ADMIN" -> "관리자";
            case "OWNER" -> "세차장 업주";
            case "CUSTOMER" -> "고객";
            default -> "담당자";
        };
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
