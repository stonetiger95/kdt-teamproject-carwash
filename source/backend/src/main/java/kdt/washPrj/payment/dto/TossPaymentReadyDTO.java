package kdt.washPrj.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TossPaymentReadyDTO {

    private String clientKey;
    private String orderId;
    private String successUrl;
    private String failUrl;
    private String providerStatus;
}
