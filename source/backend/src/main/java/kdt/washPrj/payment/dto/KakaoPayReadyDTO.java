package kdt.washPrj.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KakaoPayReadyDTO {

    private String providerTransactionId;
    private String payUrl;
    private String qrUrl;
    private String appUrl;
    private String providerStatus;
}
