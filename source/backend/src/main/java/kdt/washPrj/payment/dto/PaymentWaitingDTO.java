package kdt.washPrj.payment.dto;

import lombok.Data;

@Data
public class PaymentWaitingDTO {

    private String orderName;
    private Long washHistoryId;
    private String customerId;
    private String customerName;
    private String customerEmail;
    private String recvPhone;
    private Integer amount;
    private String paymentProvider;

}
