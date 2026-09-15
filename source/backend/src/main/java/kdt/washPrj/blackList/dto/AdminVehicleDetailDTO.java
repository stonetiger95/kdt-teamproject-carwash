package kdt.washPrj.blackList.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminVehicleDetailDTO {
    private String plateNumber;
    private Integer customerId;
    private String customerName;
    private String customerType;
    private String customerStatus;
    private String vehicleStatus;
    private Integer visitCount;
    private LocalDateTime firstEntryTime;
    private LocalDateTime lastEntryTime;
    private Boolean blacklisted;
    private Boolean manualBlacklisted;
    private Boolean suspendedBlacklisted;
    private List<AdminVehicleEntryExitDTO> entryExitHistory;
    private List<VehicleBlackListDTO> blackListHistory;

    @JsonIgnore
    private String entryExitHistoryJson;

    @JsonIgnore
    private String blacklistHistoryJson;
}
