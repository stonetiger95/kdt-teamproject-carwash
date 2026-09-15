package kdt.washPrj.reservation.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class OwnerReservationDashboardDTO {

    private LocalDate date;
    private Integer totalCount;
    private Integer confirmedCount;
    private Integer fulfilledCount;
    private Integer canceledCount;
    private Integer noShowCount;
    private List<ReservationResponseDTO> upcomingReservations;
}
