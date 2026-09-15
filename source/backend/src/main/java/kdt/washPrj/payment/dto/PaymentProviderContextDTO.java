package kdt.washPrj.payment.dto;

import lombok.Data;

@Data
public class PaymentProviderContextDTO {

    private Long paymentHistoryId;
    private Long customerId;
    private Long washHistoryId;
    private Integer amount;
    private String purpose;
    private String paymentProvider;
    private String providerTransactionId;
    private String providerStatus;
    private String status;
}
