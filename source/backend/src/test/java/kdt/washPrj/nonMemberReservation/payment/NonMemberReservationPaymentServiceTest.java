package kdt.washPrj.nonMemberReservation.payment;

import kdt.washPrj.nonMemberReservation.NonMemberReservationService;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationAccessDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationResponseDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.GuestReservationRefundPreparationDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.GuestReservationRefundResponseDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.NonMemberReservationPaymentRecordDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.NonMemberReservationPaymentRequestDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.NonMemberReservationPaymentResponseDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.NonMemberReservationPaymentTargetDTO;
import kdt.washPrj.payment.PayAppClient;
import kdt.washPrj.reservation.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NonMemberReservationPaymentServiceTest {

    private NonMemberReservationPaymentMapper mapper;
    private NonMemberReservationService reservationService;
    private PayAppClient payAppClient;
    private NonMemberReservationPaymentService paymentService;

    @BeforeEach
    void setUp() {
        mapper = mock(NonMemberReservationPaymentMapper.class);
        reservationService = mock(NonMemberReservationService.class);
        payAppClient = mock(PayAppClient.class);
        paymentService = new NonMemberReservationPaymentService(
                mapper,
                reservationService,
                payAppClient
        );

        ReflectionTestUtils.setField(
                paymentService,
                "payAppUserid",
                "test-user"
        );
        ReflectionTestUtils.setField(
                paymentService,
                "payAppLinkKey",
                "test-key"
        );
        ReflectionTestUtils.setField(
                paymentService,
                "payAppLinkValue",
                "test-value"
        );
        ReflectionTestUtils.setField(
                paymentService,
                "openPayType",
                "card"
        );
        ReflectionTestUtils.setField(
                paymentService,
                "backendBaseUrl",
                "https://backend.test"
        );
    }

    @Test
    void requestPaymentCreatesWaitingPaymentAndReturnsPayUrl() {
        NonMemberReservationPaymentRequestDTO request = requestDTO();
        NonMemberReservationPaymentTargetDTO target = targetDTO();

        when(mapper.selectPaymentTargetForUpdate(6L)).thenReturn(target);
        when(mapper.selectActivePaymentForUpdate(6L)).thenReturn(null);
        when(mapper.reservePaymentHistoryId()).thenReturn(101L);
        when(mapper.insertWaitingPayment(101L, 6L, 7000))
                .thenReturn(1);

        Map<String, String> payAppResult = new LinkedHashMap<>();
        payAppResult.put("state", "1");
        payAppResult.put("payurl", "https://pay.test/101");
        payAppResult.put("qrurl", "https://pay.test/qr/101");
        when(payAppClient.postForm(any())).thenReturn(payAppResult);

        NonMemberReservationPaymentResponseDTO response =
                paymentService.requestPayment(request);

        assertEquals(101L, response.getPaymentId());
        assertEquals(6L, response.getReservationId());
        assertEquals(7000, response.getAmount());
        assertEquals("WAITING", response.getStatus());
        assertEquals("https://pay.test/101", response.getPayUrl());
        verify(reservationService).getReservation(
                any(NonMemberReservationAccessDTO.class)
        );
    }

    @Test
    void successfulFeedbackCompletesReservation() {
        NonMemberReservationPaymentRecordDTO payment =
                new NonMemberReservationPaymentRecordDTO();
        payment.setPaymentHistoryId(101L);
        payment.setReservationId(6L);
        payment.setAmount(7000);
        payment.setPaymentStatus("WAITING");

        when(mapper.selectPaymentForUpdate(101L, 6L))
                .thenReturn(payment);
        when(mapper.updateFromFeedback(
                101L,
                6L,
                "CARD",
                "SUCCESS",
                LocalDateTime.of(2026, 8, 8, 16, 0),
                "PAYAPP-101",
                null
        )).thenReturn(1);

        Map<String, String> params = validFeedback();

        String result = paymentService.handlePayAppFeedback(params);

        assertEquals("SUCCESS", result);
        verify(reservationService).completePayment(6L);
        verify(reservationService, never()).failPayment(anyLong(), any());
    }

    @Test
    void feedbackWithInvalidCredentialsIsRejected() {
        Map<String, String> params = validFeedback();
        params.put("linkval", "wrong-value");

        String result = paymentService.handlePayAppFeedback(params);

        assertEquals("FAIL", result);
        verifyNoInteractions(mapper, reservationService);
    }

    @Test
    void refundCancellationFeedbackAppliesMatchingRefund() {
        NonMemberReservationPaymentRecordDTO payment =
                paidPaymentRecord("SUCCESS", "PAID");
        when(mapper.selectPaymentForUpdate(101L, 6L))
                .thenReturn(payment);
        when(mapper.applyGuestReservationRefundFeedback(
                101L,
                6L,
                7000,
                "PAYAPP-101",
                "state=8;transaction=PAYAPP-101;amount=7000"
        )).thenReturn("APPLIED");

        Map<String, String> params = validFeedback();
        params.put("pay_state", "8");

        assertEquals(
                "SUCCESS",
                paymentService.handlePayAppFeedback(params)
        );
        verify(mapper).applyGuestReservationRefundFeedback(
                101L,
                6L,
                7000,
                "PAYAPP-101",
                "state=8;transaction=PAYAPP-101;amount=7000"
        );
        verifyNoInteractions(reservationService);
    }

    @Test
    void completedRefundCancellationFeedbackIsIdempotent() {
        NonMemberReservationPaymentRecordDTO payment =
                paidPaymentRecord("CANCELED", "REFUNDED");
        when(mapper.selectPaymentForUpdate(101L, 6L))
                .thenReturn(payment);
        when(mapper.applyGuestReservationRefundFeedback(
                anyLong(),
                anyLong(),
                any(),
                any(),
                any()
        )).thenReturn("ALREADY_APPLIED");

        Map<String, String> params = validFeedback();
        params.put("pay_state", "8");

        assertEquals(
                "SUCCESS",
                paymentService.handlePayAppFeedback(params)
        );
    }

    @Test
    void refundCancellationFeedbackRejectsProviderMismatch() {
        NonMemberReservationPaymentRecordDTO payment =
                paidPaymentRecord("SUCCESS", "PAID");
        when(mapper.selectPaymentForUpdate(101L, 6L))
                .thenReturn(payment);
        when(mapper.applyGuestReservationRefundFeedback(
                anyLong(),
                anyLong(),
                any(),
                any(),
                any()
        )).thenReturn("PAYMENT_MISMATCH");

        Map<String, String> params = validFeedback();
        params.put("pay_state", "8");
        params.put("mul_no", "WRONG-ID");

        assertEquals(
                "FAIL",
                paymentService.handlePayAppFeedback(params)
        );
        verifyNoInteractions(reservationService);
    }

    @Test
    void refundPaidReservationPerformsFullPayAppCancellation() {
        when(reservationService.getReservation(
                any(NonMemberReservationAccessDTO.class)
        )).thenReturn(paidGuestReservation());
        when(mapper.prepareGuestReservationRefund(6L, "일정 변경"))
                .thenReturn(refundPreparation());

        Map<String, String> payAppResult = new LinkedHashMap<>();
        payAppResult.put("state", "1");
        payAppResult.put("errorMessage", "success");
        when(payAppClient.postForm(any())).thenReturn(payAppResult);
        when(mapper.completeGuestReservationRefund(anyLong(), any()))
                .thenReturn(1);

        GuestReservationRefundResponseDTO response =
                paymentService.refundPaidReservation(
                        6L,
                        "01012345678",
                        " 일정 변경 "
                );

        assertEquals(301L, response.getRefundHistoryId());
        assertEquals(7000, response.getRefundAmount());
        assertEquals("SUCCESS", response.getRefundStatus());
        assertEquals("CANCELED", response.getReservationStatus());
        assertEquals("REFUNDED", response.getPaymentStatus());
        verify(payAppClient).postForm(org.mockito.ArgumentMatchers.argThat(
                form -> "paycancel".equals(form.get("cmd"))
                        && "PAYAPP-101".equals(form.get("mul_no"))
                        && "0".equals(form.get("partcancel"))
                        && !form.containsKey("cancelprice")
        ));
        verify(mapper).completeGuestReservationRefund(anyLong(), any());
        verify(mapper, never()).failGuestReservationRefund(anyLong(), any());
    }

    @Test
    void refundPaidReservationRejectsPassedCancellationCutoff() {
        when(reservationService.getReservation(
                any(NonMemberReservationAccessDTO.class)
        )).thenReturn(paidGuestReservation());
        GuestReservationRefundPreparationDTO preparation =
                new GuestReservationRefundPreparationDTO();
        preparation.setResultCode("CANCEL_CUTOFF_PASSED");
        when(mapper.prepareGuestReservationRefund(6L, "일정 변경"))
                .thenReturn(preparation);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.refundPaidReservation(
                        6L,
                        "01012345678",
                        "일정 변경"
                )
        );

        assertEquals(409, exception.getStatusCode().value());
        verifyNoInteractions(payAppClient);
    }

    @Test
    void refundFailureMarksOnlyPreparedRefundAsFailed() {
        when(reservationService.getReservation(
                any(NonMemberReservationAccessDTO.class)
        )).thenReturn(paidGuestReservation());
        when(mapper.prepareGuestReservationRefund(6L, "일정 변경"))
                .thenReturn(refundPreparation());

        Map<String, String> payAppResult = new LinkedHashMap<>();
        payAppResult.put("state", "0");
        payAppResult.put("errorMessage", "provider rejected");
        when(payAppClient.postForm(any())).thenReturn(payAppResult);
        when(mapper.failGuestReservationRefund(anyLong(), any()))
                .thenReturn(1);

        assertThrows(
                ResponseStatusException.class,
                () -> paymentService.refundPaidReservation(
                        6L,
                        "01012345678",
                        "일정 변경"
                )
        );

        verify(mapper).failGuestReservationRefund(anyLong(), any());
        verify(mapper, never()).completeGuestReservationRefund(
                anyLong(),
                any()
        );
    }

    @Test
    void providerSuccessAndLocalFailureMarksReconciliation() {
        when(reservationService.getReservation(
                any(NonMemberReservationAccessDTO.class)
        )).thenReturn(paidGuestReservation());
        when(mapper.prepareGuestReservationRefund(6L, "일정 변경"))
                .thenReturn(refundPreparation());

        Map<String, String> payAppResult = new LinkedHashMap<>();
        payAppResult.put("state", "1");
        when(payAppClient.postForm(any())).thenReturn(payAppResult);
        when(mapper.completeGuestReservationRefund(anyLong(), any()))
                .thenReturn(0);
        when(mapper.markGuestReservationRefundReconciliationRequired(
                anyLong(),
                any()
        )).thenReturn(1);

        assertThrows(
                ResponseStatusException.class,
                () -> paymentService.refundPaidReservation(
                        6L,
                        "01012345678",
                        "일정 변경"
                )
        );

        verify(mapper).markGuestReservationRefundReconciliationRequired(
                anyLong(),
                any()
        );
        verify(mapper, never()).failGuestReservationRefund(anyLong(), any());
    }

    @Test
    void paidGuestNoShowIsRefundedAutomatically() {
        when(mapper.selectGuestNoShowRefundCandidates(20))
                .thenReturn(List.of(6L));
        when(mapper.prepareGuestNoShowRefund(
                6L,
                "비회원 예약 NO_SHOW 자동 환불"
        )).thenReturn(refundPreparation());

        Map<String, String> payAppResult = new LinkedHashMap<>();
        payAppResult.put("state", "1");
        when(payAppClient.postForm(any())).thenReturn(payAppResult);
        when(mapper.completeGuestNoShowRefund(anyLong(), any()))
                .thenReturn(1);

        paymentService.refundPaidNoShowReservations();

        verify(payAppClient).postForm(org.mockito.ArgumentMatchers.argThat(
                form -> "paycancel".equals(form.get("cmd"))
                        && "PAYAPP-101".equals(form.get("mul_no"))
                        && "0".equals(form.get("partcancel"))
        ));
        verify(mapper).completeGuestNoShowRefund(anyLong(), any());
        verify(mapper, never()).completeGuestReservationRefund(
                anyLong(), any()
        );
    }

    @Test
    void failedGuestNoShowRefundKeepsProviderFailureForRetry() {
        when(mapper.selectGuestNoShowRefundCandidates(20))
                .thenReturn(List.of(6L));
        when(mapper.prepareGuestNoShowRefund(
                6L,
                "비회원 예약 NO_SHOW 자동 환불"
        )).thenReturn(refundPreparation());

        Map<String, String> payAppResult = new LinkedHashMap<>();
        payAppResult.put("state", "0");
        payAppResult.put("errorMessage", "provider rejected");
        when(payAppClient.postForm(any())).thenReturn(payAppResult);
        when(mapper.failGuestReservationRefund(anyLong(), any()))
                .thenReturn(1);

        paymentService.refundPaidNoShowReservations();

        verify(mapper).failGuestReservationRefund(anyLong(), any());
        verify(mapper, never()).completeGuestNoShowRefund(anyLong(), any());
    }

    @Test
    void noShowRefundFeedbackUsesNoShowStateTransition() {
        NonMemberReservationPaymentRecordDTO payment =
                paidPaymentRecord("SUCCESS", "PAID");
        payment.setReservationStatus("NO_SHOW");
        when(mapper.selectPaymentForUpdate(101L, 6L))
                .thenReturn(payment);
        when(mapper.applyGuestNoShowRefundFeedback(
                101L,
                6L,
                7000,
                "PAYAPP-101",
                "state=8;transaction=PAYAPP-101;amount=7000"
        )).thenReturn("APPLIED");

        Map<String, String> params = validFeedback();
        params.put("pay_state", "8");

        assertEquals("SUCCESS", paymentService.handlePayAppFeedback(params));
        verify(mapper).applyGuestNoShowRefundFeedback(
                101L,
                6L,
                7000,
                "PAYAPP-101",
                "state=8;transaction=PAYAPP-101;amount=7000"
        );
        verify(mapper, never()).applyGuestReservationRefundFeedback(
                anyLong(), anyLong(), any(), any(), any()
        );
    }

    private NonMemberReservationPaymentRequestDTO requestDTO() {
        NonMemberReservationPaymentRequestDTO request =
                new NonMemberReservationPaymentRequestDTO();
        request.setReservationId(6L);
        request.setPhone("01012345678");
        request.setCustomerName("비회원");
        return request;
    }

    private NonMemberReservationPaymentTargetDTO targetDTO() {
        NonMemberReservationPaymentTargetDTO target =
                new NonMemberReservationPaymentTargetDTO();
        target.setReservationId(6L);
        target.setPlateNumber("01고8819");
        target.setOrderName("기본 세차");
        target.setAmount(7000);
        target.setRecvPhone("01012345678");
        target.setReservationStatus("PENDING_PAYMENT");
        target.setReservationPaymentStatus("PENDING");
        target.setPaymentExpiresAt(
                LocalDateTime.of(2026, 8, 8, 16, 15)
        );
        return target;
    }

    private Map<String, String> validFeedback() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("userid", "test-user");
        params.put("linkkey", "test-key");
        params.put("linkval", "test-value");
        params.put("var1", "101");
        params.put("var2", "GUEST_RESERVATION:6");
        params.put("price", "7000");
        params.put("pay_state", "4");
        params.put("pay_type", "1");
        params.put("pay_date", "2026-08-08 16:00:00");
        params.put("mul_no", "PAYAPP-101");
        return params;
    }

    private NonMemberReservationResponseDTO paidGuestReservation() {
        NonMemberReservationResponseDTO response =
                new NonMemberReservationResponseDTO();
        response.setReservationId(6L);
        response.setReservationType("GUEST");
        response.setStatus(ReservationStatus.CONFIRMED);
        response.setPaymentStatus("PAID");
        return response;
    }

    private GuestReservationRefundPreparationDTO refundPreparation() {
        GuestReservationRefundPreparationDTO preparation =
                new GuestReservationRefundPreparationDTO();
        preparation.setResultCode("OK");
        preparation.setRefundHistoryId(301L);
        preparation.setPaymentHistoryId(101L);
        preparation.setReservationId(6L);
        preparation.setRefundAmount(7000);
        preparation.setPayAppPaymentId("PAYAPP-101");
        return preparation;
    }

    private NonMemberReservationPaymentRecordDTO paidPaymentRecord(
            String paymentStatus,
            String reservationPaymentStatus
    ) {
        NonMemberReservationPaymentRecordDTO payment =
                new NonMemberReservationPaymentRecordDTO();
        payment.setPaymentHistoryId(101L);
        payment.setReservationId(6L);
        payment.setAmount(7000);
        payment.setPaymentStatus(paymentStatus);
        payment.setPayAppPaymentId("PAYAPP-101");
        payment.setReservationStatus(
                "REFUNDED".equals(reservationPaymentStatus)
                        ? "CANCELED"
                        : "CONFIRMED"
        );
        payment.setReservationPaymentStatus(reservationPaymentStatus);
        return payment;
    }
}
