package kdt.washPrj.nonMemberReservation;

import kdt.washPrj.reservation.ReservationQueryService;
import kdt.washPrj.reservation.dto.AvailableSlotDTO;
import kdt.washPrj.washsite.WashSiteService;
import kdt.washPrj.washsite.dto.WashSiteResponseDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/public/non-member-reservations")
public class NonMemberReservationCatalogController {

    private final WashSiteService washSiteService;
    private final ReservationQueryService reservationQueryService;

    public NonMemberReservationCatalogController(
            WashSiteService washSiteService,
            ReservationQueryService reservationQueryService
    ) {
        this.washSiteService = washSiteService;
        this.reservationQueryService = reservationQueryService;
    }

    @GetMapping("/wash-sites")
    public List<WashSiteResponseDTO> getWashSites() {
        return washSiteService.getCustomerWashSiteList();
    }

    @GetMapping("/available-slots")
    public List<AvailableSlotDTO> getAvailableSlots(
            @RequestParam Long washSiteId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return reservationQueryService.getAvailableSlots(
                washSiteId,
                date
        );
    }
}
