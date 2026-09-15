package kdt.washPrj.prepaid.dto;

import lombok.Data;

@Data
public class BillingCardRegisterRequestDTO {

    private String payAppBillingKey;
    private String paymentProvider;
    private String cardName;
    private String cardNoMasked;
    private Boolean defaultCard;
    private Boolean autoPayEnabled;
    private Integer maxAutoPayAmount;
    private Boolean testCard;

}
