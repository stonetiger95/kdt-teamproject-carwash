package kdt.washPrj.prepaid.dto;

import lombok.Data;

@Data
public class PrepaidWashPaymentTargetDTO {

    private Long washHistoryId;
    private Long customerId;
    private String orderName;
    private Integer amount;
    private String paymentStatus;

}
