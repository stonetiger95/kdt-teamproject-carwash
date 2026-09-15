package kdt.washPrj.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentProviderOptionDTO {

    private String code;
    private String label;
    private boolean enabled;
}
