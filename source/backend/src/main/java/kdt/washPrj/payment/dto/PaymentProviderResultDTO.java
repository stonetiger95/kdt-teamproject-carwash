package kdt.washPrj.payment.dto;

import lombok.Data;

@Data
public class PaymentProviderResultDTO {

    private String resultCode;
    private String purpose;
    private Long customerId;
    private Long washHistoryId;
    private String previousStatus;
    private String currentStatus;
}
