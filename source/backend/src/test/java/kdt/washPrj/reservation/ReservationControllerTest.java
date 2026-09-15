package kdt.washPrj.reservation;

import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.reservation.dto.AvailableSlotDTO;
import kdt.washPrj.reservation.dto.ReservationResponseDTO;
import kdt.washPrj.reservation.dto.OwnerReservationDashboardDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationControllerTest {

    private ReservationService reservationService;
    private ReservationQueryService reservationQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reservationService = mock(ReservationService.class);
        reservationQueryService = mock(ReservationQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ReservationController(
                        reservationService,
                        reservationQueryService
                )
        ).build();
    }

    @Test
    void customerCreatesReservationAndReceivesCreatedResponse() throws Exception {
        ReservationResponseDTO response = new ReservationResponseDTO();
        response.setReservationId(100L);
        response.setStatus(ReservationStatus.CONFIRMED);
        when(reservationService.createReservation(eq("customer01"), any()))
                .thenReturn(100L);
        when(reservationQueryService.getCustomerReservation(
                "customer01", 100L
        )).thenReturn(response);

        mockMvc.perform(post("/api/customer/reservations")
                        .principal(authentication("customer01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": 10,
                                  "washSiteId": 3,
                                  "deviceId": 8,
                                  "courseId": 5,
                                  "startTime": "2099-08-08T10:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value(100))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void customerCancelsReservationWithAuthenticatedLoginId() throws Exception {
        mockMvc.perform(patch("/api/customer/reservations/100/cancel")
                        .principal(authentication("customer01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelReason\":\"일정 변경\"}"))
                .andExpect(status().isNoContent());

        verify(reservationService).cancelCustomerReservation(
                eq("customer01"), eq(100L),
                argThat(dto -> "일정 변경".equals(dto.getCancelReason()))
        );
    }

    @Test
    void ownerCancelsReservationWithAuthenticatedLoginId() throws Exception {
        mockMvc.perform(patch("/api/owner/reservations/100/cancel")
                        .principal(authentication("owner01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelReason\":\"장비 점검\"}"))
                .andExpect(status().isNoContent());

        verify(reservationService).cancelOwnerReservation(
                eq("owner01"), eq(100L), any()
        );
    }

    @Test
    void ownerDashboardPassesLoginAndDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 8);
        OwnerReservationDashboardDTO dashboard =
                new OwnerReservationDashboardDTO();
        dashboard.setDate(date);
        dashboard.setTotalCount(4);
        dashboard.setConfirmedCount(2);
        dashboard.setFulfilledCount(1);
        dashboard.setCanceledCount(1);
        dashboard.setNoShowCount(0);
        dashboard.setUpcomingReservations(List.of());
        when(reservationQueryService.getOwnerReservationDashboard(
                "owner01", date
        )).thenReturn(dashboard);

        mockMvc.perform(get("/api/owner/reservations/dashboard")
                        .principal(authentication("owner01"))
                        .param("date", "2026-08-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.confirmedCount").value(2));

        verify(reservationQueryService)
                .getOwnerReservationDashboard("owner01", date);
    }

    @Test
    void availableSlotEndpointPassesSiteAndDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 9);
        AvailableSlotDTO slot = new AvailableSlotDTO();
        slot.setWashSiteId(3L);
        slot.setDeviceId(8L);
        when(reservationQueryService.getAvailableSlots(3L, date))
                .thenReturn(List.of(slot));

        mockMvc.perform(get("/api/customer/reservations/available-slots")
                        .param("washSiteId", "3")
                        .param("date", "2026-08-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].washSiteId").value(3))
                .andExpect(jsonPath("$[0].deviceId").value(8));
    }

    @Test
    void customerAndOwnerListEndpointsUseSeparateScopes() throws Exception {
        when(reservationQueryService.getCustomerReservationPage(
                eq("customer01"), any()
        )).thenReturn(new PageResponseDTO<>(List.of(), 1, 10, 0));
        when(reservationQueryService.getOwnerReservationPage(
                eq("owner01"), any()
        )).thenReturn(new PageResponseDTO<>(List.of(), 1, 10, 0));

        mockMvc.perform(get("/api/customer/reservations")
                        .principal(authentication("customer01")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/owner/reservations")
                        .principal(authentication("owner01")))
                .andExpect(status().isOk());

        verify(reservationQueryService).getCustomerReservationPage(
                eq("customer01"), any()
        );
        verify(reservationQueryService).getOwnerReservationPage(
                eq("owner01"), any()
        );
    }

    @Test
    void adminListAndDetailEndpointsUseAdminQueries() throws Exception {
        ReservationResponseDTO detail = new ReservationResponseDTO();
        detail.setReservationId(100L);
        when(reservationQueryService.getAdminReservationPage(
                eq("admin01"), any()
        ))
                .thenReturn(new PageResponseDTO<>(
                        List.of(), 1, 10, 0
                ));
        when(reservationQueryService.getAdminReservation(
                "admin01", 100L
        ))
                .thenReturn(detail);

        mockMvc.perform(get("/api/admin/reservations")
                        .principal(authentication("admin01"))
                        .param("ownerId", "2"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/reservations/100")
                        .principal(authentication("admin01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(100));

        verify(reservationQueryService)
                .getAdminReservationPage(
                        eq("admin01"),
                        argThat(search -> search.getOwnerId() == 2L)
                );
        verify(reservationQueryService).getAdminReservation(
                "admin01", 100L
        );
    }

    private UsernamePasswordAuthenticationToken authentication(String loginId) {
        return new UsernamePasswordAuthenticationToken(
                loginId,
                null,
                List.of()
        );
    }
}
