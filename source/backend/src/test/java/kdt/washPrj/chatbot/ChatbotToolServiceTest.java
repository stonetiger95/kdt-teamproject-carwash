package kdt.washPrj.chatbot;

import kdt.washPrj.benefit.BenefitService;
import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.payment.PaymentQueryService;
import kdt.washPrj.payment.dto.PaymentResponseDTO;
import kdt.washPrj.payment.dto.PaymentSearchCondition;
import kdt.washPrj.reservation.ReservationQueryService;
import kdt.washPrj.reservation.dto.ReservationResponseDTO;
import kdt.washPrj.reservation.dto.ReservationSearchDTO;
import kdt.washPrj.wash.WashQueryService;
import kdt.washPrj.washsite.WashSiteService;
import kdt.washPrj.washsite.dto.WashSiteMapDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatbotToolServiceTest {

    private final ReservationQueryService reservationService = mock(ReservationQueryService.class);
    private final PaymentQueryService paymentService = mock(PaymentQueryService.class);
    private final WashQueryService washService = mock(WashQueryService.class);
    private final ChatbotCustomerDataMapper customerDataMapper = mock(ChatbotCustomerDataMapper.class);
    private final BenefitService benefitService = mock(BenefitService.class);
    private final WashSiteService washSiteService = mock(WashSiteService.class);
    private final ChatbotToolService service = new ChatbotToolService(
            reservationService, paymentService, washService, customerDataMapper, benefitService,
            washSiteService
    );

    @Test
    void specificWashSitePriceQuestionReturnsCurrentDatabasePrice() {
        WashSiteMapDTO mainSite = washSite(
                "알파 무정차 세차장 본점", "알파 본점 기본 세차", 7_000
        );
        WashSiteMapDTO secondSite = washSite(
                "알파 무정차 세차장 2호점", "알파 2호점 기본 세차", 5_000
        );
        when(washSiteService.getMapWashSites(
                null, null, null, null, null, null, null, null
        )).thenReturn(List.of(mainSite, secondSite));

        ChatbotToolResult result = service.answerPublicQuestion(
                "알파 세차장 본점 가격은 어떻게 돼?"
        ).orElseThrow();

        assertEquals(ChatbotToolService.GET_WASH_SITE_PRICE, result.name());
        assertTrue(result.answer().contains("알파 무정차 세차장 본점"));
        assertTrue(result.answer().contains("7,000원"));
    }

    @Test
    void genericPriceQuestionExplainsNearbyAndSpecificPriceCommands() {
        when(washSiteService.getMapWashSites(
                null, null, null, null, null, null, null, null
        )).thenReturn(List.of(washSite("알파 무정차 세차장 본점", "기본 세차", 7_000)));

        ChatbotToolResult result = service.answerPublicQuestion(
                "세차장 운영시간과 가격은 어디서 확인하나요?"
        ).orElseThrow();

        assertEquals(ChatbotToolService.GET_WASH_SITE_PRICE, result.name());
        assertEquals(
                "근처에 있는 세차장 목록과 가격을 원하시면 \"근처 세차장\"을 입력해주시고 "
                        + "세차장 이름을 입력하시면 구체적인 가격을 알려드리겠습니다.",
                result.answer()
        );
    }

    @Test
    void nearbyWashSiteQuestionReturnsPriceAndDistanceInNearestOrder() {
        WashSiteMapDTO nearest = washSite(
                "알파 무정차 세차장 본점", "알파 본점 기본 세차", 7_000
        );
        nearest.setDistanceKm(0.84);
        WashSiteMapDTO second = washSite(
                "알파 무정차 세차장 2호점", "알파 2호점 기본 세차", 5_000
        );
        second.setDistanceKm(2.15);
        when(washSiteService.getMapWashSites(
                null, 35.1521, 129.0594, 20.0, null, null, null, null
        )).thenReturn(List.of(nearest, second));

        ChatbotToolResult result = service.answerPublicQuestion(
                "근처 세차장", 35.1521, 129.0594
        ).orElseThrow();

        assertEquals(ChatbotToolService.GET_NEARBY_WASH_SITES, result.name());
        assertTrue(result.answer().contains("알파 무정차 세차장 본점 · 7,000원 · 0.84km"));
        assertTrue(result.answer().contains("알파 무정차 세차장 2호점 · 5,000원 · 2.15km"));
    }

    @Test
    void nearbyWashSiteQuestionWithoutLocationRequestsPermission() {
        ChatbotToolResult result = service.answerPublicQuestion("근처 세차장")
                .orElseThrow();

        assertEquals(ChatbotToolService.GET_NEARBY_WASH_SITES, result.name());
        assertTrue(result.answer().contains("위치 권한"));
    }

    @Test
    void specificWashSiteLocationQuestionReturnsMapCoordinates() {
        WashSiteMapDTO mainSite = washSite(
                "알파 무정차 세차장 본점", "알파 본점 기본 세차", 7_000
        );
        mainSite.setWashSiteId(1L);
        mainSite.setAddress("부산 부산진구 신천대로50번길 79");
        mainSite.setLatitude(35.1521);
        mainSite.setLongitude(129.0594);
        when(washSiteService.getMapWashSites(
                null, null, null, null, null, null, null, null
        )).thenReturn(List.of(mainSite));

        ChatbotToolResult result = service.answerPublicQuestion(
                "알파 세차장 본점 위치 알려줘"
        ).orElseThrow();

        assertEquals(ChatbotToolService.GET_WASH_SITE_LOCATION, result.name());
        assertTrue(result.answer().contains("부산 부산진구 신천대로50번길 79"));
        assertEquals(1L, result.washSite().washSiteId());
        assertEquals(35.1521, result.washSite().latitude());
        assertEquals(129.0594, result.washSite().longitude());
    }

    @Test
    void recognizesKoreanParticlesInReservationLookupQuestion() {
        assertEquals(
                ChatbotToolService.GET_MY_RESERVATIONS,
                service.detectTool("예약을 확인하고 싶어").orElseThrow()
        );
    }

    @Test
    void recognizesPaymentHistoryLookupQuestion() {
        assertEquals(
                ChatbotToolService.GET_MY_PAYMENTS,
                service.detectTool("결제 내역을 확인하고 싶어").orElseThrow()
        );
    }

    @Test
    void reservationLookupUsesOnlyAuthenticatedLoginIdAndHidesIdentifiers() {
        ReservationResponseDTO reservation = new ReservationResponseDTO();
        reservation.setSiteName("강남 세차장");
        reservation.setStartTime(LocalDateTime.of(2026, 8, 26, 10, 0));
        reservation.setCustomerPhone("010-1234-5678");
        reservation.setPlateNumber("12가3456");
        when(reservationService.getCustomerReservationPage(any(), any()))
                .thenReturn(new PageResponseDTO<>(List.of(reservation), 1, 5, 1));

        ChatbotToolResult result = service.execute(
                ChatbotToolService.GET_MY_RESERVATIONS,
                new ChatbotUserContext("customer-a", "ROLE_CUSTOMER", true)
        );

        ArgumentCaptor<String> loginId = ArgumentCaptor.forClass(String.class);
        verify(reservationService).getCustomerReservationPage(
                loginId.capture(), any(ReservationSearchDTO.class)
        );
        assertEquals("customer-a", loginId.getValue());
        assertFalse(result.answer().contains("010-1234-5678"));
        assertFalse(result.answer().contains("12가3456"));
    }

    @Test
    void anonymousUserCannotExecuteCustomerTool() {
        assertThrows(IllegalStateException.class, () -> service.execute(
                ChatbotToolService.GET_MY_RESERVATIONS,
                ChatbotUserContext.anonymous()
        ));
    }

    @Test
    void paymentLookupUsesAuthenticatedLoginIdAndReturnsMinimalFields() {
        PaymentResponseDTO payment = new PaymentResponseDTO();
        payment.setOrderId("secret-order-id");
        payment.setCustomerEmail("customer@example.com");
        payment.setPaymentKey("secret-payment-key");
        payment.setPurpose("WASH_PAYMENT");
        payment.setAmount(12_000);
        payment.setStatus("SUCCESS");
        payment.setApprovedAt(LocalDateTime.of(2026, 8, 25, 14, 30));
        when(paymentService.getPaymentList(any(), any(), any()))
                .thenReturn(List.of(payment));

        ChatbotToolResult result = service.execute(
                ChatbotToolService.GET_MY_PAYMENTS,
                new ChatbotUserContext("customer-a", "ROLE_CUSTOMER", true)
        );

        verify(paymentService).getPaymentList(
                org.mockito.ArgumentMatchers.eq("customer-a"),
                org.mockito.ArgumentMatchers.eq("ROLE_CUSTOMER"),
                any(PaymentSearchCondition.class)
        );
        assertTrue(result.answer().contains("12,000원"));
        assertTrue(result.answer().contains("결제 완료"));
        assertFalse(result.answer().contains("secret-order-id"));
        assertFalse(result.answer().contains("customer@example.com"));
        assertFalse(result.answer().contains("secret-payment-key"));
    }

    private WashSiteMapDTO washSite(String siteName, String courseName, int price) {
        WashSiteMapDTO site = new WashSiteMapDTO();
        site.setSiteName(siteName);
        site.setCourseName(courseName);
        site.setWashPrice(price);
        return site;
    }
}
