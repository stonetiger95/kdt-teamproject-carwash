package kdt.washPrj.washCycle;

import lombok.Data;

@Data
public class WashCycleStateDTO {

    private Integer washHistoryId;
    private Long reservationId;
    private Integer deviceId;
    private Long reservedDeviceId;
    private String reservationStatus;
    private Integer customerId;
    private Integer vehicleId;
    private Integer washSiteId;
    private Integer courseId;
    private String customerType;
    private String paymentStatus;
    private String washStatus;
}
