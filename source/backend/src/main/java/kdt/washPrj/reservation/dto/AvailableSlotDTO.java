package kdt.washPrj.reservation.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AvailableSlotDTO {

    private Long washSiteId;
    private String siteName;
    private Long deviceId;
    private String deviceName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
