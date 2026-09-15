package kdt.washPrj.plateDetection.dto;

import lombok.Data;

@Data
public class PlateDetectionLogReviewDTO {
    private String reviewStatus; //리뷰 상태

    private String correctedPlateNumber; //관리자가 육안으로 확인하고 수정한 번호판

    private PlateBoundingBoxDTO reviewedBox;
    private String bboxReviewStatus;

    private String failureReason; // 실패이유 : 관리자 작성? 선택하는 방법?

    private Boolean trainingUseYn; // 재학습에 쓸건가요?

    private String reviewMemo; // 메모
}
