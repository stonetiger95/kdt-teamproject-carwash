package kdt.washPrj.notification.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BusinessNotificationTargetDTO {

    private String channel;
    private String recipient;
    private String targetLoginId;
    private String targetRole;
    private String actorRole;
    private String title;
    private String siteName;
    private String plateNumber;
    private LocalDateTime reservationStartTime;
    private Integer amount;
    private String paymentPurpose;
    private String cancelReason;
}
