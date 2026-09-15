package kdt.washPrj.nonMemberReservation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import kdt.washPrj.reservation.ReservationStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NonMemberReservationResponseDTO {
    private Long reservationId;
    private String reservationType;
    private String plateNumber;
    private String maskedPhone;
    private Long washSiteId;
    private String siteName;
    private Long deviceId;
    private String deviceName;
    private Long courseId;
    private String courseName;
    private Integer reservationPrice;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ReservationStatus status;
    private String paymentStatus;
    private LocalDateTime paymentExpiresAt;
    private String cancelReason;
    private LocalDateTime canceledAt;
    private LocalDateTime createdAt;
    private Boolean paymentRequired;
}
