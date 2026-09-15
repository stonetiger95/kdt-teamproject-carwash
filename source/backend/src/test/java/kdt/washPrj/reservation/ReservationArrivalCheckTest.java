package kdt.washPrj.reservation;

import kdt.washPrj.reservation.dto.ReservationArrivalCheckDTO;
import kdt.washPrj.reservation.dto.ReservationResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationArrivalCheckTest {

    private ReservationMapper reservationMapper;
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationMapper = mock(ReservationMapper.class);
        reservationService = new ReservationService(
                reservationMapper,
                new ReservationTimeProvider()
        );
    }

    @Test
    void checkArrivalReservationReturnsMatchedWithinWindow() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 8, 15, 30);
        ReservationResponseDTO reservation = reservation(start, 12L);
        when(reservationMapper.selectReservationForArrivalCheck(
                1L, "01고8819", start.minusMinutes(30)
        )).thenReturn(reservation);
        when(reservationMapper.selectAvailableDeviceForArrival(
                1L, 12L, 5L, start.minusMinutes(30)
        )).thenReturn(5L);

        ReservationArrivalCheckDTO result =
                reservationService.checkArrivalReservation(
                        1L, "01고-8819", start.minusMinutes(30)
                );

        assertEquals(ReservationArrivalStatus.MATCHED, result.getStatus());
        assertEquals(12L, result.getReservationId());
        assertEquals(5L, result.getAssignedDeviceId());
    }

    @Test
    void checkArrivalReservationAssignsAlternativeDevice() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 8, 15, 30);
        LocalDateTime arrival = start.minusMinutes(10);
        ReservationResponseDTO reservation = reservation(start, 12L);
        when(reservationMapper.selectReservationForArrivalCheck(
                1L, "01고8819", arrival
        )).thenReturn(reservation);
        when(reservationMapper.selectAvailableDeviceForArrival(
                1L, 12L, 5L, arrival
        )).thenReturn(8L);

        ReservationArrivalCheckDTO result =
                reservationService.checkArrivalReservation(
                        1L, "01고8819", arrival
                );

        assertEquals(ReservationArrivalStatus.MATCHED, result.getStatus());
        assertEquals(5L, result.getReservedDeviceId());
        assertEquals(8L, result.getAssignedDeviceId());
    }

    @Test
    void checkArrivalReservationReturnsNoDevice() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 8, 15, 30);
        LocalDateTime arrival = start.minusMinutes(10);
        ReservationResponseDTO reservation = reservation(start, 12L);
        when(reservationMapper.selectReservationForArrivalCheck(
                1L, "01고8819", arrival
        )).thenReturn(reservation);

        ReservationArrivalCheckDTO result =
                reservationService.checkArrivalReservation(
                        1L, "01고8819", arrival
                );

        assertEquals(ReservationArrivalStatus.NO_DEVICE, result.getStatus());
        assertNull(result.getAssignedDeviceId());
    }

    @Test
    void checkArrivalReservationAllowsArrivalBeforePreviousEarlyWindow() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 8, 15, 30);
        ReservationResponseDTO reservation = reservation(start, 12L);
        LocalDateTime arrival = start.minusMinutes(31);
        when(reservationMapper.selectReservationForArrivalCheck(
                1L, "01고8819", arrival
        )).thenReturn(reservation);
        when(reservationMapper.selectAvailableDeviceForArrival(
                1L, 12L, 5L, arrival
        )).thenReturn(5L);

        ReservationArrivalCheckDTO result =
                reservationService.checkArrivalReservation(
                        1L, "01고8819", arrival
                );

        assertEquals(ReservationArrivalStatus.MATCHED, result.getStatus());
        assertEquals(5L, result.getAssignedDeviceId());
        verify(reservationMapper, never()).recordArrivalDeviation(
                12L, "ARRIVED_EARLY", arrival
        );
    }

    @Test
    void checkArrivalReservationReturnsTooLate() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 8, 15, 30);
        ReservationResponseDTO reservation = reservation(start, 12L);
        LocalDateTime arrival = start.plusMinutes(16);
        when(reservationMapper.selectReservationForArrivalCheck(
                1L, "01고8819", arrival
        )).thenReturn(reservation);
        when(reservationMapper.selectAvailableDeviceForArrival(
                1L, 12L, 5L, arrival
        )).thenReturn(8L);

        ReservationArrivalCheckDTO result =
                reservationService.checkArrivalReservation(
                        1L, "01고8819", arrival
                );

        assertEquals(ReservationArrivalStatus.TOO_LATE, result.getStatus());
        assertEquals(8L, result.getAssignedDeviceId());
        verify(reservationMapper).recordArrivalDeviation(
                12L, "ARRIVED_LATE", arrival
        );
    }

    @Test
    void checkArrivalReservationReturnsNotFound() {
        LocalDateTime arrival = LocalDateTime.of(2026, 8, 8, 15, 0);
        when(reservationMapper.selectReservationForArrivalCheck(
                1L, "01고8819", arrival
        )).thenReturn(null);

        ReservationArrivalCheckDTO result =
                reservationService.checkArrivalReservation(
                        1L, "01고8819", arrival
                );

        assertEquals(ReservationArrivalStatus.NOT_FOUND, result.getStatus());
    }

    @Test
    void checkArrivalReservationValidatesInput() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.checkArrivalReservation(
                        1L, " ", LocalDateTime.now()
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(reservationMapper);
    }

    private ReservationResponseDTO reservation(
            LocalDateTime start,
            Long reservationId
    ) {
        ReservationResponseDTO result = new ReservationResponseDTO();
        result.setReservationId(reservationId);
        result.setDeviceId(5L);
        result.setStartTime(start);
        result.setEndTime(start.plusMinutes(30));
        return result;
    }
}
