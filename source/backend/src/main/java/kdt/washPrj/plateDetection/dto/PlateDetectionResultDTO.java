package kdt.washPrj.plateDetection.dto;

import lombok.Data;

@Data
public class PlateDetectionResultDTO {
    private String detectedPlateNumber;
    private Double confidence;
    private Double yoloConfidence;
    private Double ocrConfidence;
    private PlateBoundingBoxDTO bbox;

    private Boolean success;
    private String message;
}