package kdt.washPrj.vehicle;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VehicleVO {
    private Integer vehicleId;
    private Integer customerId;
    private String plateNumber;
    private String vehicleType;
    private String vehicleStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean manualBlacklisted;
    private Boolean suspendedBlacklisted;
}
