package kdt.washPrj.blackList;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VehicleBlackListVO {
    private Long vehicleBlacklistId;
    private Integer customerId;
    private String plateNumber;
    private String blacklistType;
    private String reason;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime releasedAt;
}
