package kdt.washPrj.nonMemberReservation;

import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationResponseDTO;
import kdt.washPrj.nonMemberReservation.payment.NonMemberReservationPaymentService;
import kdt.washPrj.nonMemberReservation.payment.dto.GuestReservationRefundResponseDTO;
import kdt.washPrj.reservation.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NonMemberReservationControllerTest {

    private NonMemberReservationService service;
    private NonMemberReservationPaymentService paymentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(NonMemberReservationService.class);
        paymentService = mock(NonMemberReservationPaymentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new NonMemberReservationController(service, paymentService)
        ).build();
    }

    @Test
    void createReturnsPendingPaymentReservation() throws Exception {
        NonMemberReservationResponseDTO response = response();
        when(service.createPendingReservation(any())).thenReturn(response);

        mockMvc.perform(post("/api/public/non-member-reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "plateNumber": "12가3456",
                              "phone": "01012345678",
                              "washSiteId": 1,
                              "deviceId": 2,
                              "courseId": 5,
                              "startTime": "2099-08-09T10:00:00"
                            }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value(100))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.authCode").doesNotExist());
    }

    @Test
    void lookupUsesBodyCredentials() throws Exception {
        when(service.getReservation(any())).thenReturn(response());

        mockMvc.perform(post("/api/public/non-member-reservations/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "reservationId": 100,
                              "phone": "01012345678"
                            }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(100));
    }

    @Test
    void cancelReturnsNoContent() throws Exception {
        when(service.getReservation(any())).thenReturn(response());

        mockMvc.perform(patch("/api/public/non-member-reservations/100/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "phone": "01012345678",
                              "cancelReason": "일정 변경"
                            }
                        """))
                .andExpect(status().isNoContent());

        verify(service).cancelReservation(eq(100L), any());
    }

    @Test
    void paidReservationCancelReturnsRefundResult() throws Exception {
        NonMemberReservationResponseDTO reservation = response();
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setPaymentStatus("PAID");
        when(service.getReservation(any())).thenReturn(reservation);

        GuestReservationRefundResponseDTO refund =
                new GuestReservationRefundResponseDTO();
        refund.setReservationId(100L);
        refund.setPaymentHistoryId(200L);
        refund.setRefundHistoryId(300L);
        refund.setReservationStatus("CANCELED");
        refund.setPaymentStatus("REFUNDED");
        refund.setRefundAmount(15000);
        refund.setRefundStatus("SUCCESS");
        when(paymentService.refundPaidReservation(
                eq(100L),
                eq("01012345678"),
                eq("일정 변경")
        )).thenReturn(refund);

        mockMvc.perform(patch("/api/public/non-member-reservations/100/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "phone": "01012345678",
                              "cancelReason": "일정 변경"
                            }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(100))
                .andExpect(jsonPath("$.paymentHistoryId").value(200))
                .andExpect(jsonPath("$.refundHistoryId").value(300))
                .andExpect(jsonPath("$.reservationStatus").value("CANCELED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.refundAmount").value(15000))
                .andExpect(jsonPath("$.refundStatus").value("SUCCESS"));

        verify(paymentService).refundPaidReservation(
                eq(100L),
                eq("01012345678"),
                eq("일정 변경")
        );
    }

    private NonMemberReservationResponseDTO response() {
        NonMemberReservationResponseDTO response =
                new NonMemberReservationResponseDTO();
        response.setReservationId(100L);
        response.setStatus(ReservationStatus.PENDING_PAYMENT);
        response.setPaymentStatus("PENDING");
        response.setPaymentRequired(true);
        return response;
    }
}
