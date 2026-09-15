package kdt.washPrj.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentReconciliationReviewDTO {

    @NotBlank
    @Pattern(regexp = "SUCCESS|CANCELED")
    private String resolution;

    @Size(max = 30)
    private String paymentMethod;

    @NotNull
    @PastOrPresent
    private LocalDateTime providerProcessedAt;

    @NotBlank
    @Size(max = 500)
    private String reviewNote;
}
