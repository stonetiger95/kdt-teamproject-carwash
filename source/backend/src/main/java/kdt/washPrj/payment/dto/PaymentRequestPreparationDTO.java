package kdt.washPrj.payment.dto;

import lombok.Data;

@Data
public class PaymentRequestPreparationDTO {

    private String resultCode;
    private Long paymentHistoryId;
    private String orderName;
    private Long washHistoryId;
    private Integer amount;
}
