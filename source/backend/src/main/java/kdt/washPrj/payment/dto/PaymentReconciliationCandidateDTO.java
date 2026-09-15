package kdt.washPrj.payment.dto;

import lombok.Data;

@Data
public class PaymentReconciliationCandidateDTO {

    private Long paymentHistoryId;
    private String paymentProvider;
    private String providerTransactionId;
    private Integer amount;
    private String purpose;
    private Integer reconciliationAttempts;
}
