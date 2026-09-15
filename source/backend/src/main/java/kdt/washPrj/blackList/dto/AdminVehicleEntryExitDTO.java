package kdt.washPrj.blackList.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminVehicleEntryExitDTO {
    private Integer entryExitId;
    private Integer vehicleId;
    private Integer customerId;
    private Integer entryDetectionLogId;
    private Integer exitDetectionLogId;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private String status;
}
