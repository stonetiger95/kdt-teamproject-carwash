package kdt.washPrj.notification.dto;

import lombok.Data;

@Data
public class BusinessNotificationEventDTO {

    private Long eventId;
    private String eventType;
    private Long entityId;
    private Integer attempts;
}
