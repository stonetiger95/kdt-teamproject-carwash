package kdt.washPrj.plateDetection.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PlateDetectionLogDetailDTO {
    // 식별자 관련
    private Integer plateDetectionLogId; // 번호판 탐지 id
    private Integer washHistoryId;       // 연결된 세차기록 id
    private Integer deviceId;            // 촬영한 장비
    // 실제 표시  데이터
    private String siteName;            // 세차장 이름
    private String deviceName;          // 장비 이름

    // 번호판 탐지 관련 데이터
    private String detectedPlateNumber;  // 탐지된 번호판
    private LocalDateTime detectedTime;  // 탐지 시간
    private Double yoloConfidence;       // Fast API에서 받아온 YOLO 신뢰도
    private Double ocrConfidence;        // Fast API에서 받아온 OCR 신뢰도
    private Integer yoloX1;
    private Integer yoloY1;
    private Integer yoloX2;
    private Integer yoloY2;
    private Boolean success;             // errorLog 존재 여부 기준의 탐지 성공 여부
    private String errorLog;             // 에러 발생시 로그 저장

    //파일 관련 데이터
    private String filePath;             // 사진이 저장되는 경로
    private LocalDateTime fileExpireTime;// 만료 후 7일 기준으로 파일 정리

    // 리뷰 관련 데이터
    private Boolean needReview; // 리뷰 필요 여부
    private String reviewStatus; // 리뷰 상태
    private String correctedPlateNumber; // 관리자가 확인한 실제 번호판
    private String failureReason; // 실패/의심 사유
    private Boolean trainingUseYn; // 재학습 데이터 사용 여부
    private String reviewMemo; // 리뷰 메모
    private LocalDateTime reviewedAt; // 리뷰 처리 시간
    private Integer reviewerId; // 리뷰 처리 관리자 ID
    private String bboxReviewStatus;

    private Integer reviewedX1;
    private Integer reviewedY1;
    private Integer reviewedX2;
    private Integer reviewedY2;
}
