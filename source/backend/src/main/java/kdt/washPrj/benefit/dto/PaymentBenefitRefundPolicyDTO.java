package kdt.washPrj.benefit.dto;

import lombok.Data;

@Data
public class PaymentBenefitRefundPolicyDTO {
    private Long paymentHistoryId;
    private Integer listPrice;
    private Integer couponDiscountAmount;
    private Integer mileageUsedAmount;
    private Integer payableAmount;
    private Integer mileageEarnedAmount;
    private Boolean partialRefundAllowed;
    private String policyMessage;
}
