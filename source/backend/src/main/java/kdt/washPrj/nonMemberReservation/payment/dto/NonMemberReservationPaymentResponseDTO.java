package kdt.washPrj.nonMemberReservation.payment.dto;

import lombok.Data;

@Data
public class NonMemberReservationPaymentResponseDTO {

    private Long paymentId;
    private Long reservationId;
    private String orderId;
    private String orderName;
    private String customerName;
    private String customerEmail;
    private String recvPhone;
    private Integer amount;
    private String payUrl;
    private String qrUrl;
    private String status;
}
