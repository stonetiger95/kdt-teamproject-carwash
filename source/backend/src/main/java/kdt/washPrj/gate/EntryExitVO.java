package kdt.washPrj.gate;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EntryExitVO {
    private Integer entryExitId;
    private String plateNumber;

    private Integer vehicleId;
    private Integer customerId;

    private Integer entryDetectionLogId;
    private Integer exitDetectionLogId;

    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private String status;
}