package kdt.washPrj.prepaid.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class BillingCardDTO {

    private Long payAppCardId;
    private Long customerId;
    @JsonIgnore
    private String payAppBillingKey;
    private String paymentProvider;
    private String cardName;
    private String cardNoMasked;
    private String status;
    private Boolean defaultCard;
    private Boolean autoPayEnabled;
    private Integer maxAutoPayAmount;

}
