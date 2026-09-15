package kdt.washPrj.gate.dto;

import kdt.washPrj.plateDetection.dto.PlateDetectionResponseDTO;
import lombok.Data;

@Data
public class GateResponseDTO {
    private Integer entryExitId;            //입출차기록 ID
    private Integer washHistoryId;          //세차기록 ID
    private Integer plateDetectionLogId;    //번호판 탐지 ID

    private String plateNumber;             //탐지된 번호판 결과
    private Double yoloConfidence;              //신뢰도 !수정예정!
    private Double ocrConfidence;              //신뢰도 !수정예정!

    private Boolean matchedVehicle;         //일치 차량 여부
    private Boolean barrierOpen;            //게이트 이벤트 TRUE/FALSE

    private Integer vehicleId;              //차량 id
    private Integer customerId;             //고객 id
    private String customerType;            //고객 종류

    private Boolean reservationMatched;     //입차 가능한 예약 일치 여부
    private Long reservationId;             //일치한 예약 ID
    private String reservationStatus;       //예약 도착 검증 결과
    private Long reservedDeviceId;           //예약 당시 세차기 ID
    private Long assignedDeviceId;           //실제 안내할 세차기 ID

    private String paymentStatus;           //결제 상태
    private String washStatus;              //세차 상태
    private String eventType;               //이벤트 종류
    private String message;                 //출력 메시지

    public GateResponseDTO() {
    }

    public GateResponseDTO(PlateDetectionResponseDTO detection){
        this.plateDetectionLogId = detection.getPlateDetectionLogId();
        this.plateNumber = detection.getDetectedPlateNumber();
        this.ocrConfidence = detection.getOcrConfidence();
        this.yoloConfidence = detection.getYoloConfidence();
    }

    public static GateResponseDTO gateFromDetection(
            PlateDetectionResponseDTO detection
    ){
        return new GateResponseDTO(detection);
    }
}
