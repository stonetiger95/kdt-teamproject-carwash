package kdt.washPrj.businessHour;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class BusinessHourDTO {

    @NotNull(message = "요일은 필수입니다.")
    @Min(value = 1, message = "요일은 1 이상이어야 합니다.")
    @Max(value = 7, message = "요일은 7 이하여야 합니다.")
    private Integer dayOfWeek;

    private LocalTime openTime;
    private LocalTime closeTime;

    @NotNull(message = "휴무 여부는 필수입니다.")
    private Boolean closed;

    @NotNull(message = "24시간 영업 여부는 필수입니다.")
    private Boolean twentyFourHours;
}
