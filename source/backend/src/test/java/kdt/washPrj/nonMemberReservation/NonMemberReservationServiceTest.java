package kdt.washPrj.nonMemberReservation;

import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationAccessDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationAuthRecordDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationCancelDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationCreateDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationResponseDTO;
import kdt.washPrj.reservation.ReservationStatus;
import kdt.washPrj.reservation.ReservationPolicy;
import kdt.washPrj.reservation.ReservationTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NonMemberReservationServiceTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 8, 9, 0);

    private NonMemberReservationMapper mapper;
    private PasswordEncoder passwordEncoder;
    private NonMemberReservationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(NonMemberReservationMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new NonMemberReservationService(
                mapper,
                passwordEncoder,
                new ReservationTimeProvider(Clock.fixed(
                        Instant.parse("2026-08-08T00:00:00Z"),
                        ReservationPolicy.ZONE_ID
                ))
        );
    }

    @Test
    void createPendingReservationKeepsAccessSecretInternal() {
        NonMemberReservationCreateDTO request = request();
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-code");
        when(mapper.insertPendingReservation(
                eq(request), eq("12가3456"), eq("01012345678"),
                eq("encoded-code")
        )).thenReturn(100L);
        when(mapper.insertHistory(
                eq(100L), eq("CREATED_BY_GUEST"), isNull(),
                eq("PENDING_PAYMENT"), eq("GUEST"), isNull()
        )).thenReturn(1);
        when(mapper.selectReservation(100L)).thenReturn(response(
                100L, ReservationStatus.PENDING_PAYMENT, "PENDING"
        ));

        NonMemberReservationResponseDTO result =
                service.createPendingReservation(request);

        assertEquals(100L, result.getReservationId());
        assertTrue(result.getPaymentRequired());
        verify(passwordEncoder).encode(anyString());
        verify(mapper).existsPlateTimeConflict(
                eq("12가3456"),
                eq(request.getStartTime()),
                eq(request.getStartTime().plusMinutes(30))
        );
    }

    @Test
    void createPendingReservationRejectsPlateConflict() {
        NonMemberReservationCreateDTO request = request();
        when(mapper.existsPlateTimeConflict(anyString(), any(), any()))
                .thenReturn(true);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createPendingReservation(request)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(mapper, never()).insertPendingReservation(
                any(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void getReservationRequiresReservationIdAndPhone() {
        NonMemberReservationAccessDTO request = new NonMemberReservationAccessDTO();
        request.setReservationId(100L);
        request.setPhone("010-1234-5678");
        when(mapper.selectAuthRecord(100L)).thenReturn(authRecord(
                "PENDING_PAYMENT", "PENDING"
        ));
        when(mapper.selectReservation(100L)).thenReturn(response(
                100L, ReservationStatus.PENDING_PAYMENT, "PENDING"
        ));

        NonMemberReservationResponseDTO result =
                service.getReservation(request);

        assertEquals(100L, result.getReservationId());
        assertTrue(result.getPaymentRequired());
    }

    @Test
    void cancelPaidReservationStopsAtPaymentTodoBoundary() {
        NonMemberReservationCancelDTO request = cancelRequest();
        when(mapper.selectAuthRecord(100L)).thenReturn(authRecord(
                "CONFIRMED", "PAID"
        ));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.cancelReservation(100L, request)
        );

        assertEquals(HttpStatus.NOT_IMPLEMENTED, error.getStatusCode());
        verify(mapper, never()).cancelPendingReservation(anyLong(), anyString());
    }

    @Test
    void completePaymentIsReadyForPaymentCallbackConnection() {
        when(mapper.confirmPayment(100L)).thenReturn(1);
        when(mapper.insertHistory(
                100L, "PAYMENT_COMPLETED", "PENDING_PAYMENT",
                "CONFIRMED", "SYSTEM", null
        )).thenReturn(1);

        service.completePayment(100L);

        verify(mapper).confirmPayment(100L);
        verify(mapper).insertHistory(
            100L, "PAYMENT_COMPLETED", "PENDING_PAYMENT",
            "CONFIRMED", "SYSTEM", null
        );
    }

    @Test
    void expirationSchedulerUsesGuestReservationPaymentDbApi() {
        when(mapper.expireGuestReservationPayments()).thenReturn(2);

        service.expirePendingReservations();

        verify(mapper).expireGuestReservationPayments();
    }

    private NonMemberReservationCreateDTO request() {
        NonMemberReservationCreateDTO request =
                new NonMemberReservationCreateDTO();
        request.setPlateNumber("12가-3456");
        request.setPhone("010-1234-5678");
        request.setWashSiteId(1L);
        request.setDeviceId(2L);
        request.setCourseId(5L);
        request.setStartTime(NOW.plusDays(1).withHour(10));
        return request;
    }

    private NonMemberReservationCancelDTO cancelRequest() {
        NonMemberReservationCancelDTO request =
                new NonMemberReservationCancelDTO();
        request.setPhone("010-1234-5678");
        request.setCancelReason("일정 변경");
        return request;
    }

    private NonMemberReservationAuthRecordDTO authRecord(
            String status,
            String paymentStatus
    ) {
        NonMemberReservationAuthRecordDTO result =
                new NonMemberReservationAuthRecordDTO();
        result.setReservationId(100L);
        result.setGuestPhone("01012345678");
        result.setGuestAuthCodeHash("encoded-code");
        result.setStatus(status);
        result.setPaymentStatus(paymentStatus);
        return result;
    }

    private NonMemberReservationResponseDTO response(
            Long reservationId,
            ReservationStatus status,
            String paymentStatus
    ) {
        NonMemberReservationResponseDTO result =
                new NonMemberReservationResponseDTO();
        result.setReservationId(reservationId);
        result.setStatus(status);
        result.setPaymentStatus(paymentStatus);
        return result;
    }
}
