package kdt.washPrj.vehicle.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VehicleResponseDTO {
    private Integer vehicleId;
    private String plateNumber;
    private String vehicleType;
    private String vehicleStatus;
    private String usageStatus;
    private LocalDateTime lastWashAt;
}
