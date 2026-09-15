package kdt.washPrj.payment.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentResponseDTO {

    private Long paymentId;
    private Long refundablePaymentId;
    private Long washHistoryId;
    private String plateNumber;
    private String orderId;
    private String orderName;
    private String customerId;
    private String customerName;
    private String customerEmail;
    private Integer amount;
    private String paymentKey;
    private String payAppPaymentId;
    private String paymentProvider;
    private String providerTransactionId;
    private String providerStatus;
    private String method;
    private String status;
    private String purpose;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime canceledAt;
    private String cancelReason;
    private String receiptUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
