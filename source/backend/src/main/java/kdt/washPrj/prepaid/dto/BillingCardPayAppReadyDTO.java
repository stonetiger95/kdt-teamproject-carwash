package kdt.washPrj.prepaid.dto;

import lombok.Data;

import java.util.Map;

@Data
public class BillingCardPayAppReadyDTO {

    private String status;
    private String message;
    private String payUrl;
    private String qrUrl;
    private String actionUrl;
    private String returnUrl;
    private Map<String, String> formParams;

}
