package kdt.washPrj.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import kdt.washPrj.payment.dto.RefundReasonCode;
import lombok.Data;

@Data
public class InquiryRefundRequestDTO {

    @NotNull
    @PositiveOrZero
    private Integer refundAmount;

    @NotBlank
    @Size(max = 500)
    private String reason;

    private RefundReasonCode refundReasonCode = RefundReasonCode.CUSTOMER_REQUEST;
}
