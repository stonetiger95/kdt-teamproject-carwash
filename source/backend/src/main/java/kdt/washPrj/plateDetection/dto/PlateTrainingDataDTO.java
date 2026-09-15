package kdt.washPrj.plateDetection.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlateTrainingDataDTO {
        private Integer plateDetectionLogId;

        private String filePath;
        private LocalDateTime detectedTime;

        // 모델 예측 결과
        private String detectedPlateNumber;
        private Double yoloConfidence;
        private Double ocrConfidence;

        private Integer yoloX1;
        private Integer yoloY1;
        private Integer yoloX2;
        private Integer yoloY2;

        // 검수 확정 결과
        private String correctedPlateNumber;
        private String finalPlateNumber;

        private String reviewStatus;
        private String bboxReviewStatus;

        private Integer reviewedX1;
        private Integer reviewedY1;
        private Integer reviewedX2;
        private Integer reviewedY2;

        private LocalDateTime reviewedAt;
        private Integer reviewerId;
}
