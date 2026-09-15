package kdt.washPrj.wash.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class WashHistorySearchDTO {

    @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
    private int page = 1;

    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    private int size = 10;

    private Long ownerId;
    private Long washSiteId;

    @Size(max = 20, message = "차량번호 검색어는 20자 이하여야 합니다.")
    private String plateNumber;

    @Size(max = 20)
    private String customerType;

    @Size(max = 20)
    private String washStatus;

    @Size(max = 20)
    private String paymentStatus;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    public int getOffset() {
        return (page - 1) * size;
    }
}
