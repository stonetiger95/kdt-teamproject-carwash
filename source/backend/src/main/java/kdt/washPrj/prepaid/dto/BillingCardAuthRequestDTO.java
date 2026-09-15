package kdt.washPrj.prepaid.dto;

import lombok.Data;

@Data
public class BillingCardAuthRequestDTO {

    private Long payAppAuthRequestId;
    private Long customerId;
    private String payAppRequestId;
    private Integer amount;
    private Boolean defaultCard;
    private Boolean autoPayEnabled;
    private Integer maxAutoPayAmount;
    private String status;
    private String resultCode;
    private String resultMessage;
    private String payAppBillingKey;
    private String payAppTransactionId;

}
