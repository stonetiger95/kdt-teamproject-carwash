package kdt.washPrj.payment.dto;

import lombok.Data;

@Data
public class PaymentRefundPreparationDTO {
    private String resultCode;
    private Long refundHistoryId;
    private Long paymentHistoryId;
    private Long customerId;
    private Long washHistoryId;
    private Long walletId;
    private Integer refundAmount;
    private Integer originalAmount;
    private String refundType;
    private String payappPaymentId;
    private String paymentProvider;
    private String providerTransactionId;
}
