package kdt.washPrj.reservation.dto;

import kdt.washPrj.reservation.ReservationStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReservationResponseDTO {

    private Long reservationId;
    private String reservationType;
    private String paymentStatus;

    private Long customerId;
    private String customerName;
    private String customerPhone;

    private Long vehicleId;
    private String plateNumber;

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

    private String cancelReason;
    private LocalDateTime canceledAt;
    private LocalDateTime fulfilledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
