package kdt.washPrj.prepaid.dto;

import lombok.Data;

@Data
public class BillingCardPayAppRequestDTO {

    private String cardNo;
    private String expMonth;
    private String expYear;
    private String buyerAuthNo;
    private String cardPw;
    private String buyerName;
    private Boolean defaultCard;
    private Boolean autoPayEnabled;
    private Integer maxAutoPayAmount;
    private String recvPhone;

}
