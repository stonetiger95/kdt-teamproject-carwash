package kdt.washPrj.prepaid.dto;

import lombok.Data;

@Data
public class PrepaidChargeRequestDTO {

    private Integer amount;
    private String orderName;
    private String recvPhone;
    private String paymentProvider;
    private String returnClient;

}
