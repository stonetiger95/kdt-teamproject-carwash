package kdt.washPrj.prepaid.dto;

import lombok.Data;

@Data
public class BillingCardTestRequestDTO {

    private String cardName;
    private String cardLast4;
    private Boolean defaultCard;
    private Boolean autoPayEnabled;
    private Integer maxAutoPayAmount;

}
