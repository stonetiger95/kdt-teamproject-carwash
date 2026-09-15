package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentResponseDTO;
import kdt.washPrj.payment.dto.PaymentSearchCondition;
import kdt.washPrj.payment.dto.PaymentWaitingDTO;
import kdt.washPrj.payment.dto.PaymentWaitingResponseDTO;
import kdt.washPrj.prepaid.PrepaidService;
import kdt.washPrj.prepaid.dto.PrepaidChargeRequestDTO;
import kdt.washPrj.prepaid.dto.PrepaidChargeResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceCoverageTest {

    private PaymentQueryMapper queryMapper;
    private PrepaidService prepaidService;
    private PaymentQueryService queryService;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        queryMapper = mock(PaymentQueryMapper.class);
        prepaidService = mock(PrepaidService.class);
        queryService = mock(PaymentQueryService.class);
        service = new PaymentService(
                queryMapper,
                mock(PaymentCommandMapper.class),
                prepaidService,
                queryService,
                mock(PayAppClient.class),
                mock(PaymentProviderRegistry.class),
                mock(PaymentReconciliationService.class),
                mock(KakaoPayService.class),
                mock(TossPaymentsService.class)
        );
    }

    @Test
    void getCheckoutDefaultReturnsCustomerChargeDefaults() {
        PaymentWaitingDTO expected = new PaymentWaitingDTO();
        when(queryMapper.selectCustomerIdByLoginId("customer01"))
                .thenReturn(7L);
        when(queryMapper.selectPrepaidChargeDefault(7L))
                .thenReturn(expected);

        PaymentWaitingDTO actual =
                service.getCheckoutDefault("customer01", "ROLE_CUSTOMER");

        assertSame(expected, actual);
        verify(queryMapper).selectPrepaidChargeDefault(7L);
    }

    @Test
    void waitingPaymentMapsPrepaidChargeResponse() {
        PaymentWaitingDTO request = new PaymentWaitingDTO();
        request.setAmount(10_000);
        request.setOrderName("선불 충전");
        request.setRecvPhone("01012345678");
        request.setCustomerName("고객");
        request.setCustomerEmail("customer@example.com");

        PrepaidChargeResponseDTO charge = new PrepaidChargeResponseDTO();
        charge.setPaymentHistoryId(33L);
        charge.setCustomerId(7L);
        charge.setAmount(10_000);
        charge.setPayUrl("https://pay.example/33");
        charge.setQrUrl("https://pay.example/33/qr");
        when(prepaidService.charge(
                any(PrepaidChargeRequestDTO.class),
                eq("customer01"),
                eq("ROLE_CUSTOMER")
        )).thenReturn(charge);

        PaymentWaitingResponseDTO actual =
                service.waitingPayment(request, "customer01", "ROLE_CUSTOMER");

        assertEquals(33L, actual.getPaymentId());
        assertEquals("CHARGE-33", actual.getOrderId());
        assertEquals(10_000, actual.getAmount());
        assertEquals("WAITING", actual.getStatus());
    }

    @Test
    void getPaymentListDelegatesToQueryService() {
        PaymentSearchCondition condition = new PaymentSearchCondition();
        List<PaymentResponseDTO> expected = List.of(new PaymentResponseDTO());
        when(queryService.getPaymentList(
                "admin01",
                "ROLE_ADMIN",
                condition
        )).thenReturn(expected);

        List<PaymentResponseDTO> actual =
                service.getPaymentList("admin01", "ROLE_ADMIN", condition);

        assertSame(expected, actual);
    }

    @Test
    void getPaymentDelegatesToQueryService() {
        PaymentResponseDTO expected = new PaymentResponseDTO();
        when(queryService.getPayment(9L, "admin01", "ROLE_ADMIN"))
                .thenReturn(expected);

        PaymentResponseDTO actual =
                service.getPayment(9L, "admin01", "ROLE_ADMIN");

        assertSame(expected, actual);
    }

    @Test
    void getNonMemberCheckoutDefaultReturnsMappedTarget() {
        PaymentWaitingDTO expected = new PaymentWaitingDTO();
        when(queryMapper.selectNonMemberCheckoutDefault(205L))
                .thenReturn(expected);

        PaymentWaitingDTO actual =
                service.getNonMemberCheckoutDefault(205L);

        assertSame(expected, actual);
    }

    @Test
    void getNonMemberPaymentListReturnsMappedPayments() {
        List<PaymentResponseDTO> expected = List.of(new PaymentResponseDTO());
        when(queryMapper.selectNonMemberPaymentList(205L))
                .thenReturn(expected);

        List<PaymentResponseDTO> actual =
                service.getNonMemberPaymentList(205L);

        assertSame(expected, actual);
    }

}
