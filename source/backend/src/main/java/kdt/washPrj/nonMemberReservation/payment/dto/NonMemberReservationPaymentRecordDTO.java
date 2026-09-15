package kdt.washPrj.nonMemberReservation.payment.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NonMemberReservationPaymentRecordDTO {

    private Long paymentHistoryId;
    private Long reservationId;
    private Integer amount;
    private String paymentType;
    private String paymentStatus;
    private String purpose;
    private String payAppPaymentId;
    private String reservationStatus;
    private String reservationPaymentStatus;
    private LocalDateTime paymentExpiresAt;
}
