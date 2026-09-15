package kdt.washPrj.reservation;

import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.reservation.dto.AvailableSlotDTO;
import kdt.washPrj.reservation.dto.ReservationResponseDTO;
import kdt.washPrj.reservation.dto.ReservationSearchDTO;
import kdt.washPrj.reservation.dto.OwnerReservationDashboardDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationQueryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 8);

    private ReservationMapper reservationMapper;
    private ReservationQueryService reservationQueryService;

    @BeforeEach
    void setUp() {
        reservationMapper = mock(ReservationMapper.class);
        reservationQueryService = new ReservationQueryService(
                reservationMapper,
                new ReservationTimeProvider(Clock.fixed(
                        Instant.parse("2026-08-08T00:00:00Z"),
                        ReservationPolicy.ZONE_ID
                ))
        );
    }

    @Test
    void getAvailableSlotsReturnsMapperResult() {
        LocalDate date = TODAY.plusDays(1);
        List<AvailableSlotDTO> expected = List.of(new AvailableSlotDTO());
        when(reservationMapper.selectAvailableSlots(3L, date))
                .thenReturn(expected);

        List<AvailableSlotDTO> result =
                reservationQueryService.getAvailableSlots(3L, date);

        assertSame(expected, result);
    }

    @Test
    void getAvailableSlotsRejectsPastOrBeyondSevenDays() {
        ResponseStatusException past = assertThrows(
                ResponseStatusException.class,
                () -> reservationQueryService.getAvailableSlots(
                        3L, TODAY.minusDays(1)
                )
        );
        ResponseStatusException tooFar = assertThrows(
                ResponseStatusException.class,
                () -> reservationQueryService.getAvailableSlots(
                        3L, TODAY.plusDays(8)
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, past.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, tooFar.getStatusCode());
        verifyNoInteractions(reservationMapper);
    }

    @Test
    void getCustomerReservationPageTrimsPlateAndBuildsPage() {
        ReservationSearchDTO search = new ReservationSearchDTO();
        search.setPage(2);
        search.setSize(5);
        search.setPlateNumber(" 12가3456 ");
        List<ReservationResponseDTO> content =
                List.of(new ReservationResponseDTO());
        when(reservationMapper.selectCustomerReservationList(
                "customer01", search
        )).thenReturn(content);
        when(reservationMapper.countCustomerReservationPage(
                "customer01", search
        )).thenReturn(12);

        PageResponseDTO<ReservationResponseDTO> result =
                reservationQueryService.getCustomerReservationPage(
                        "customer01", search
                );

        assertEquals("12가3456", search.getPlateNumber());
        assertSame(content, result.getContent());
        assertEquals(2, result.getPage());
        assertEquals(5, result.getSize());
        assertEquals(12, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
    }

    @Test
    void reservationPageRejectsReversedDateRange() {
        ReservationSearchDTO search = new ReservationSearchDTO();
        search.setStartDate(LocalDate.of(2026, 8, 10));
        search.setEndDate(LocalDate.of(2026, 8, 9));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationQueryService.getCustomerReservationPage(
                        "customer01", search
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(reservationMapper);
    }

    @Test
    void customerCannotReadReservationOutsideLoginScope() {
        when(reservationMapper.selectCustomerReservation(
                "customer01", 100L
        )).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationQueryService.getCustomerReservation(
                        "customer01", 100L
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void ownerCannotReadReservationOutsideOwnedSites() {
        when(reservationMapper.selectOwnerReservation(
                "owner01", 100L
        )).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationQueryService.getOwnerReservation(
                        "owner01", 100L
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getOwnerReservationPageUsesOwnerScopedMapperQueries() {
        ReservationSearchDTO search = new ReservationSearchDTO();
        List<ReservationResponseDTO> content =
                List.of(new ReservationResponseDTO());
        when(reservationMapper.selectOwnerReservationPage(
                "owner01", search
        )).thenReturn(content);
        when(reservationMapper.countOwnerReservationPage(
                "owner01", search
        )).thenReturn(1);

        PageResponseDTO<ReservationResponseDTO> result =
                reservationQueryService.getOwnerReservationPage(
                        "owner01", search
                );

        assertSame(content, result.getContent());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getOwnerReservationDashboardUsesOwnerScopeAndTodayByDefault() {
        LocalDate today = TODAY;
        OwnerReservationDashboardDTO dashboard =
                new OwnerReservationDashboardDTO();
        List<ReservationResponseDTO> upcoming =
                List.of(new ReservationResponseDTO());
        when(reservationMapper.selectOwnerReservationDashboard(
                "owner01", today
        )).thenReturn(dashboard);
        when(reservationMapper.selectOwnerUpcomingReservations(
                "owner01", today
        )).thenReturn(upcoming);

        OwnerReservationDashboardDTO result =
                reservationQueryService.getOwnerReservationDashboard(
                        "owner01", null
                );

        assertSame(dashboard, result);
        assertSame(upcoming, result.getUpcomingReservations());
    }

    @Test
    void getOwnerReservationDashboardRequiresLogin() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationQueryService
                        .getOwnerReservationDashboard(" ", null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(reservationMapper);
    }

    @Test
    void getAdminReservationPageUsesAdminMapperQueries() {
        ReservationSearchDTO search = new ReservationSearchDTO();
        search.setOwnerId(2L);
        List<ReservationResponseDTO> content =
                List.of(new ReservationResponseDTO());
        when(reservationMapper.selectAdminReservationPage(
                "admin01", search
        ))
                .thenReturn(content);
        when(reservationMapper.countAdminReservationPage(
                "admin01", search
        ))
                .thenReturn(1);

        PageResponseDTO<ReservationResponseDTO> result =
                reservationQueryService.getAdminReservationPage(
                        "admin01", search
                );

        assertSame(content, result.getContent());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void adminReservationDetailRequiresExistingReservation() {
        ReservationResponseDTO expected = new ReservationResponseDTO();
        expected.setReservationId(100L);
        when(reservationMapper.selectAdminReservation("admin01", 100L))
                .thenReturn(expected);

        assertSame(
                expected,
                reservationQueryService.getAdminReservation(
                        "admin01", 100L
                )
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationQueryService.getAdminReservation(
                        "admin01", 200L
                )
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
