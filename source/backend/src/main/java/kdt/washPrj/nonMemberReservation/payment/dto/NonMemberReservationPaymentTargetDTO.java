package kdt.washPrj.nonMemberReservation.payment.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NonMemberReservationPaymentTargetDTO {

    private Long reservationId;
    private String plateNumber;
    private String orderName;
    private Integer amount;
    private String recvPhone;
    private String reservationStatus;
    private String reservationPaymentStatus;
    private LocalDateTime paymentExpiresAt;
}
