package kdt.washPrj.blackList.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VehicleBlackListDTO {
    private Long vehicleBlacklistId;
    private Integer customerId;
    private String blacklistType;
    private String plateNumber;
    private String reason;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime releasedAt;

    @JsonIgnore
    private Long totalElements;
}
