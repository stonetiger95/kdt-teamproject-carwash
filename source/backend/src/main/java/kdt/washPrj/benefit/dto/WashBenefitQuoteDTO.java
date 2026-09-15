package kdt.washPrj.benefit.dto;

import lombok.Data;

@Data
public class WashBenefitQuoteDTO {
    private Long washHistoryId;
    private Integer listPrice;
    private Integer couponDiscountAmount;
    private Integer mileageUsedAmount;
    private Integer payableAmount;
    private Boolean partialRefundAllowed;
}
