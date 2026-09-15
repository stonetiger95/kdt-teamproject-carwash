package kdt.washPrj.nonMemberReservation.dto;

import lombok.Data;

@Data
public class NonMemberReservationAuthRecordDTO {
    private Long reservationId;
    private String guestPhone;
    private String guestAuthCodeHash;
    private String status;
    private String paymentStatus;
}
