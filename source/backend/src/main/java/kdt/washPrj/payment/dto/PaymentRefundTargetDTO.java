package kdt.washPrj.payment.dto;

import lombok.Data;

@Data
public class PaymentRefundTargetDTO {

    private Long paymentHistoryId;
    private Long washHistoryId;
    private Long customerId;
    private Long walletId;
    private Integer amount;
    private Integer walletBalance;
    private String paymentType;
    private String purpose;
    private String status;
    private String payAppPaymentId;
    private Long useHistoryId;
    private String useStatus;
}
