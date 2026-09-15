package kdt.washPrj.nonMemberReservation.payment.dto;

import lombok.Data;

@Data
public class GuestReservationRefundResponseDTO {
    private Long reservationId;
    private Long paymentHistoryId;
    private Long refundHistoryId;
    private Integer refundAmount;
    private String refundStatus;
    private String reservationStatus;
    private String paymentStatus;
}
