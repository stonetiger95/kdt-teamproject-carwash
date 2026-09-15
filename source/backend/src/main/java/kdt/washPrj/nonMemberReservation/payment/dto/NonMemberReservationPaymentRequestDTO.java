package kdt.washPrj.nonMemberReservation.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class NonMemberReservationPaymentRequestDTO {

    @NotNull
    @Positive
    private Long reservationId;

    @NotBlank
    private String phone;

    private String customerName;
    private String customerEmail;
}
