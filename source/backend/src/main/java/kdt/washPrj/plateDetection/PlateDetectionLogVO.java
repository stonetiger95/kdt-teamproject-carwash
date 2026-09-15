package kdt.washPrj.plateDetection;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlateDetectionLogVO {
    private Integer plateDetectionLogId;
    private Integer washHistoryId;
    private Integer deviceId;
    private String detectedPlateNumber;
    private LocalDateTime detectedTime;
    private Double yoloConfidence;
    private Double ocrConfidence;
    private String errorLog;
    private String filePath;
    private LocalDateTime fileExpireTime;

    private Integer yoloX1;
    private Integer yoloY1;
    private Integer yoloX2;
    private Integer yoloY2;
}
