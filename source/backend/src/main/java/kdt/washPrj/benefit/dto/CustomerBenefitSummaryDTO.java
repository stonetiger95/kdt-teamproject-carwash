package kdt.washPrj.benefit.dto;

import lombok.Data;

@Data
public class CustomerBenefitSummaryDTO {
    private Integer totalMileageBalance;
    private Long totalAvailableCouponCount;
    private Long activeProgramCount;
}
