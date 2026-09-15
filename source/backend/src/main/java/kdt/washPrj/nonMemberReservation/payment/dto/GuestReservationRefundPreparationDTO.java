package kdt.washPrj.nonMemberReservation.payment.dto;

import lombok.Data;

@Data
public class GuestReservationRefundPreparationDTO {
    private String resultCode;
    private Long refundHistoryId;
    private Long paymentHistoryId;
    private Long reservationId;
    private Integer refundAmount;
    private String payAppPaymentId;
}
