package kdt.washPrj.prepaid.dto;

import lombok.Data;

@Data
public class PrepaidChargeResponseDTO {

    private Long paymentHistoryId;
    private Long chargeHistoryId;
    private Long walletId;
    private Long customerId;
    private Integer amount;
    private Integer balance;
    private String payUrl;
    private String qrUrl;
    private String appUrl;
    private String status;
    private String paymentProvider;
    private String providerClientKey;
    private String orderId;
    private String successUrl;
    private String failUrl;

}
