package kdt.washPrj.washCycle;

import kdt.washPrj.plateDetection.dto.PlateDetectionResponseDTO;
import lombok.Data;


@Data
public class WashCycleResponseDTO {

    private Integer plateDetectionLogId;
    private String plateNumber;
    private Double yoloConfidence;
    private Double ocrConfidence;

    private Integer entryExitId;
    private Integer washHistoryId;

    private Integer customerId;
    private Integer vehicleId;
    private String customerType;

    private Integer washSiteId;
    private Integer deviceId;
    private Integer courseId;

    private Boolean reservationMatched;
    private Long reservationId;
    private String reservationStatus;
    private Long reservedDeviceId;
    private Long assignedDeviceId;

    private String eventType;
    private String washStatus;
    private String paymentStatus;

    //자동결제 관련
    private Boolean autoPaymentSuccess;
    private Integer paidAmount;
    private Integer walletBalanceAfter;

    private Boolean accepted;
    private String message;


    public WashCycleResponseDTO() {
    }

    public WashCycleResponseDTO(PlateDetectionResponseDTO detection) {
        this.plateDetectionLogId = detection.getPlateDetectionLogId();
        this.plateNumber = detection.getDetectedPlateNumber();
        this.ocrConfidence = detection.getOcrConfidence();
        this.yoloConfidence = detection.getYoloConfidence();
    }

    public static WashCycleResponseDTO washCycleFromDetection(
            PlateDetectionResponseDTO detection
    ) {
        return new WashCycleResponseDTO(detection);
    }
}
