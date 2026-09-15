package kdt.washPrj.prepaid.dto;

import lombok.Data;

@Data
public class PrepaidChargePreparationDTO {

    private String resultCode;
    private Long paymentHistoryId;
    private Long walletId;
    private Long customerId;
    private Integer balance;
}
