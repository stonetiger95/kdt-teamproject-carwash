package kdt.washPrj.plateDetection.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlateDetectionLogListDTO {
    private Integer plateDetectionLogId;
    private LocalDateTime detectedTime;
    private String detectedPlateNumber;
    private String siteName;
    private String deviceName;
    private Boolean success;

    // 리뷰가 필요할만한 데이터에 대한 표시
    // 백엔드 SQL에서 계산될 예정
    private Boolean needReview;
}
