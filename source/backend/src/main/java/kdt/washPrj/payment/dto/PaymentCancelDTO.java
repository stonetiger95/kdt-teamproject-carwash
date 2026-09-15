package kdt.washPrj.payment.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentCancelDTO {

    @PositiveOrZero
    private Integer refundAmount;

    @Size(max = 500)
    private String cancelReason;

    private RefundReasonCode refundReasonCode = RefundReasonCode.CUSTOMER_REQUEST;

}
