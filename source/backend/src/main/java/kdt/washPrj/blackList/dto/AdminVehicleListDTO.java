package kdt.washPrj.blackList.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminVehicleListDTO {
    private String plateNumber;
    private Integer customerId;
    private String customerName;
    private String customerType;
    private String customerStatus;
    private String vehicleStatus;
    private Integer visitCount;
    private LocalDateTime lastEntryTime;
    private Boolean blacklisted;

    @JsonIgnore
    private Long totalElements;
}
