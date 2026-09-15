package kdt.washPrj.reservation.dto;

import kdt.washPrj.reservation.ReservationArrivalStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReservationArrivalCheckDTO {

    private ReservationArrivalStatus status;
    private Long reservationId;
    private String reservationType;
    private String paymentStatus;
    private Long reservedDeviceId;
    private Long assignedDeviceId;
    private Long courseId;
    private Integer reservationPrice;
    private LocalDateTime reservationStartTime;
    private LocalDateTime reservationEndTime;
    private String message;

    public static ReservationArrivalCheckDTO notFound(
            String message
    ) {
        ReservationArrivalCheckDTO result = new ReservationArrivalCheckDTO();
        result.status = ReservationArrivalStatus.NOT_FOUND;
        result.message = message;
        return result;
    }
}
