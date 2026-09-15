package kdt.washPrj.reservation;

import kdt.washPrj.reservation.dto.ReservationCancelDTO;
import kdt.washPrj.reservation.dto.ReservationCreateDTO;
import kdt.washPrj.reservation.dto.ReservationResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationServiceTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 8, 9, 0);

    private ReservationMapper reservationMapper;
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationMapper = mock(ReservationMapper.class);
        reservationService = new ReservationService(
                reservationMapper,
                fixedTimeProvider()
        );
    }

    @Test
    void createReservationUsesAtomicDatabaseCommand() {
        ReservationCreateDTO request = validCreateRequest();
        when(reservationMapper.insertReservation("customer01", request))
                .thenReturn(100L);

        Long reservationId = reservationService.createReservation(
                "customer01",
                request
        );

        assertEquals(100L, reservationId);
        verify(reservationMapper).insertReservation("customer01", request);
        verifyNoMoreInteractions(reservationMapper);
    }

    @Test
    void createReservationMapsDatabaseRaceToConflict() {
        ReservationCreateDTO request = validCreateRequest();
        when(reservationMapper.insertReservation("customer01", request))
                .thenThrow(new DataIntegrityViolationException("overlap"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.createReservation("customer01", request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void createReservationRejectsInvalidDatabaseSelection() {
        ReservationCreateDTO request = validCreateRequest();
        when(reservationMapper.insertReservation(
                "customer01", request
        )).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.createReservation(
                        "customer01", request
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void createReservationRejectsInvalidTimeRules() {
        ReservationCreateDTO tooSoon = validCreateRequest();
        tooSoon.setStartTime(NOW.plusMinutes(30));
        assertBadRequest(tooSoon);

        ReservationCreateDTO tooFar = validCreateRequest();
        tooFar.setStartTime(NOW.plusDays(8));
        assertBadRequest(tooFar);

        ReservationCreateDTO wrongMinute = validCreateRequest();
        wrongMinute.setStartTime(
                validCreateRequest().getStartTime().withMinute(10)
        );
        assertBadRequest(wrongMinute);

        ReservationCreateDTO hasSeconds = validCreateRequest();
        hasSeconds.setStartTime(hasSeconds.getStartTime().plusSeconds(1));
        assertBadRequest(hasSeconds);

        verifyNoInteractions(reservationMapper);
    }

    @Test
    void createReservationAllowsExactOneHourBoundary() {
        ReservationCreateDTO request = validCreateRequest();
        request.setStartTime(NOW.plusHours(1));
        when(reservationMapper.insertReservation("customer01", request))
                .thenReturn(100L);

        assertEquals(
                100L,
                reservationService.createReservation("customer01", request)
        );
    }

    @Test
    void cancelCustomerReservationTrimsReasonAndUsesAtomicCommand() {
        ReservationCancelDTO request = cancelRequest("  일정 변경  ");
        when(reservationMapper.cancelCustomerReservation(
                "customer01", 100L, request
        )).thenReturn(1);

        reservationService.cancelCustomerReservation(
                "customer01", 100L, request
        );

        assertEquals("일정 변경", request.getCancelReason());
        verify(reservationMapper).cancelCustomerReservation(
                "customer01", 100L, request
        );
    }

    @Test
    void cancelCustomerReservationRejectsUnauthorizedOrLateReservation() {
        ReservationCancelDTO request = cancelRequest("취소");
        when(reservationMapper.cancelCustomerReservation(
                "customer01", 100L, request
        )).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.cancelCustomerReservation(
                        "customer01", 100L, request
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void cancelOwnerReservationUsesAtomicCommand() {
        ReservationCancelDTO request = cancelRequest("장비 점검");
        when(reservationMapper.cancelOwnerReservation(
                "owner01", 100L, request
        )).thenReturn(1);

        reservationService.cancelOwnerReservation("owner01", 100L, request);

        verify(reservationMapper).cancelOwnerReservation(
                "owner01", 100L, request
        );
    }

    @Test
    void cancelReservationRejectsBlankOrOversizedReason() {
        ReservationCancelDTO blank = cancelRequest("   ");
        ReservationCancelDTO oversized = cancelRequest("a".repeat(501));

        ResponseStatusException blankException = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.cancelOwnerReservation(
                        "owner01", 100L, blank
                )
        );
        ResponseStatusException oversizedException = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.cancelCustomerReservation(
                        "customer01", 100L, oversized
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                blankException.getStatusCode()
        );
        assertEquals(
                HttpStatus.BAD_REQUEST,
                oversizedException.getStatusCode()
        );
        verify(reservationMapper, never()).cancelOwnerReservation(
                anyString(), anyLong(), any()
        );
        verify(reservationMapper, never()).cancelCustomerReservation(
                anyString(), anyLong(), any()
        );
    }

    @Test
    void findArrivalReservationNormalizesPlateWithoutCompletingIt() {
        LocalDateTime entryTime = LocalDateTime.of(2026, 8, 8, 10, 0);
        ReservationResponseDTO reservation = new ReservationResponseDTO();
        reservation.setReservationId(100L);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        when(reservationMapper.selectArrivalReservationForUpdate(
                3L, "12가3456", entryTime
        )).thenReturn(reservation);
        ReservationResponseDTO result =
                reservationService.findArrivalReservationForWash(
                        3L, " 12가-3456 ", entryTime
                );

        assertSame(reservation, result);
        assertEquals(ReservationStatus.CONFIRMED, result.getStatus());
        verify(reservationMapper, never()).fulfillReservation(anyLong());
    }

    @Test
    void findArrivalReservationReturnsNullForWalkInVehicle() {
        LocalDateTime entryTime = LocalDateTime.of(2026, 8, 8, 10, 0);

        ReservationResponseDTO result =
                reservationService.findArrivalReservationForWash(
                        3L, "12가3456", entryTime
                );

        assertNull(result);
        verify(reservationMapper, never()).fulfillReservation(anyLong());
    }

    @Test
    void completeReservationRejectsAlreadyConsumedReservation() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.completeReservation(100L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void completeReservationUsesAtomicDatabaseCommand() {
        when(reservationMapper.fulfillReservation(100L)).thenReturn(1);

        reservationService.completeReservation(100L);

        verify(reservationMapper).fulfillReservation(100L);
    }

    @Test
    void findArrivalReservationRejectsInvalidArrivalInformation() {
        LocalDateTime entryTime = LocalDateTime.of(
                2026, 8, 8, 10, 0
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                assertThrows(
                        ResponseStatusException.class,
                        () -> reservationService.findArrivalReservationForWash(
                                0L, "12가3456", entryTime
                        )
                ).getStatusCode()
        );
        assertEquals(
                HttpStatus.BAD_REQUEST,
                assertThrows(
                        ResponseStatusException.class,
                        () -> reservationService.findArrivalReservationForWash(
                                3L, "   ", entryTime
                        )
                ).getStatusCode()
        );
        assertEquals(
                HttpStatus.BAD_REQUEST,
                assertThrows(
                        ResponseStatusException.class,
                        () -> reservationService.findArrivalReservationForWash(
                                3L, "12가3456", null
                        )
                ).getStatusCode()
        );

        verifyNoInteractions(reservationMapper);
    }

    @Test
    void markExpiredReservationsDelegatesNoShowUpdate() {
        LocalDateTime noShowCutoff = NOW.minusMinutes(15);
        when(reservationMapper.updateExpiredReservationsToNoShow(
                noShowCutoff
        ))
                .thenReturn(2);

        reservationService.markExpiredReservationsAsNoShow();

        verify(reservationMapper).updateExpiredReservationsToNoShow(
                noShowCutoff
        );
    }

    private void assertBadRequest(ReservationCreateDTO request) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.createReservation("customer01", request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private ReservationCreateDTO validCreateRequest() {
        ReservationCreateDTO request = new ReservationCreateDTO();
        request.setVehicleId(10L);
        request.setWashSiteId(3L);
        request.setDeviceId(8L);
        request.setCourseId(5L);
        request.setStartTime(NOW.plusHours(2));
        return request;
    }

    private ReservationTimeProvider fixedTimeProvider() {
        return new ReservationTimeProvider(Clock.fixed(
                Instant.parse("2026-08-08T00:00:00Z"),
                ReservationPolicy.ZONE_ID
        ));
    }

    private LocalDateTime nextHalfHour(LocalDateTime time) {
        LocalDateTime normalized = time.withSecond(0).withNano(0);
        if (normalized.getMinute() == 0 || normalized.getMinute() == 30) {
            return normalized;
        }
        int minutesToAdd = normalized.getMinute() < 30
                ? 30 - normalized.getMinute()
                : 60 - normalized.getMinute();
        return normalized.plusMinutes(minutesToAdd);
    }

    private ReservationCancelDTO cancelRequest(String reason) {
        ReservationCancelDTO request = new ReservationCancelDTO();
        request.setCancelReason(reason);
        return request;
    }
}
