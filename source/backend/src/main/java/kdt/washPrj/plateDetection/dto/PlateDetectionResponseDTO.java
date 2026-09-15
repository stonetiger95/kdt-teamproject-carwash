package kdt.washPrj.plateDetection.dto;

import lombok.Data;

@Data
public class PlateDetectionResponseDTO {
    private Integer plateDetectionLogId;
    private String detectedPlateNumber;
    private Double yoloConfidence;
    private Double ocrConfidence;
    private Boolean success;
    private String message;
}
