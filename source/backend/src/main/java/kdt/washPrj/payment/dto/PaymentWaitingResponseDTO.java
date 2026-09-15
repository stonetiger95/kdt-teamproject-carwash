package kdt.washPrj.payment.dto;

import lombok.Data;

@Data
public class PaymentWaitingResponseDTO {

    private Long paymentId;
    private String orderId;
    private String orderName;
    private String customerId;
    private String customerName;
    private String customerEmail;
    private String recvPhone;
    private Integer amount;
    private String payUrl;
    private String qrUrl;
    private String appUrl;
    private String status;
    private String paymentProvider;
    private String providerClientKey;
    private String successUrl;
    private String failUrl;

}
