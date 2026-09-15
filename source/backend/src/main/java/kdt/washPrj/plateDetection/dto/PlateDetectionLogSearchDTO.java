package kdt.washPrj.plateDetection.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PlateDetectionLogSearchDTO {
    private int page = 1;
    private int size = 10;
    private Boolean failedOnly = false;
    private Boolean needReviewOnly = false;
    private LocalDate startDate;
    private LocalDate endDate;
    private String plateNumber;

    public int getOffset() {
        return (page - 1) * size;
    }
}
