package kdt.washPrj.prepaid;

import kdt.washPrj.payment.PayAppClient;
import kdt.washPrj.prepaid.dto.BillingCardDTO;
import kdt.washPrj.prepaid.dto.BillingCardRegisterRequestDTO;
import kdt.washPrj.prepaid.dto.BillingCardTestRequestDTO;
import kdt.washPrj.prepaid.dto.PrepaidUseRequestDTO;
import kdt.washPrj.prepaid.dto.PrepaidUseResponseDTO;
import kdt.washPrj.prepaid.dto.PrepaidWalletDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrepaidServiceTest {

    private PrepaidMapper prepaidMapper;
    private PrepaidQueryService prepaidQueryService;
    private PrepaidService prepaidService;

    @BeforeEach
    void setUp() {
        prepaidMapper = mock(PrepaidMapper.class);
        prepaidQueryService = mock(PrepaidQueryService.class);
        prepaidService = new PrepaidService(
                prepaidMapper,
                mock(PayAppClient.class),
                prepaidQueryService,
                mock(kdt.washPrj.payment.PaymentProviderRegistry.class),
                mock(kdt.washPrj.payment.PaymentReconciliationService.class),
                mock(kdt.washPrj.payment.KakaoPayService.class),
                mock(kdt.washPrj.payment.TossPaymentsService.class)
        );
    }

    @Test
    void getWalletRejectsNonCustomerRole() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> prepaidService.getWallet("owner01", "OWNER")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void getWalletDelegatesToQueryService() {
        PrepaidWalletDTO wallet = new PrepaidWalletDTO();
        when(prepaidQueryService.getWallet("customer01")).thenReturn(wallet);

        PrepaidWalletDTO result = prepaidService.getWallet("customer01", "CUSTOMER");

        assertSame(wallet, result);
    }

    @Test
    void getBillingCardsDelegatesToQueryService() {
        List<BillingCardDTO> cards = List.of(new BillingCardDTO());
        when(prepaidQueryService.getBillingCards("customer01", "CUSTOMER"))
                .thenReturn(cards);

        List<BillingCardDTO> result =
                prepaidService.getBillingCards("customer01", "CUSTOMER");

        assertSame(cards, result);
    }

    @Test
    void registerBillingCardRejectsNullRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> prepaidService.registerBillingCard(null, "customer01", "CUSTOMER")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void registerBillingCardRequiresBillingKey() {
        when(prepaidMapper.selectCustomerIdByLoginId("customer01")).thenReturn(1L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> prepaidService.registerBillingCard(
                        new BillingCardRegisterRequestDTO(),
                        "customer01",
                        "CUSTOMER"
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void registerBillingCardRejectsUnknownCustomer() {
        BillingCardRegisterRequestDTO request = new BillingCardRegisterRequestDTO();
        request.setPayAppBillingKey("billing-key");
        when(prepaidMapper.selectCustomerIdByLoginId("missing")).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> prepaidService.registerBillingCard(request, "missing", "CUSTOMER")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void registerBillingCardRejectsDisabledTestCard() {
        BillingCardRegisterRequestDTO request = new BillingCardRegisterRequestDTO();
        request.setTestCard(true);
        when(prepaidMapper.selectCustomerIdByLoginId("customer01")).thenReturn(1L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> prepaidService.registerBillingCard(request, "customer01", "CUSTOMER")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void registerTestBillingCardRejectsInvalidLastFourDigits() throws Exception {
        setField("payAppAutoBillingTestCardEnabled", true);
        BillingCardTestRequestDTO request = new BillingCardTestRequestDTO();
        request.setCardLast4("12AB");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> prepaidService.registerTestBillingCard(
                        request,
                        "customer01",
                        "CUSTOMER"
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void deleteBillingCardRejectsMissingCardId() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> prepaidService.deleteBillingCard(null, "customer01", "CUSTOMER")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void deleteBillingCardReturnsNotFoundWhenNoRowIsUpdated() {
        when(prepaidMapper.selectCustomerIdByLoginId("customer01")).thenReturn(1L);
        when(prepaidMapper.deleteBillingCard(1L, 15L)).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> prepaidService.deleteBillingCard(15L, "customer01", "CUSTOMER")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void completeChargeDelegatesToDatabaseFunction() {
        when(prepaidMapper.applyConfirmedCharge(31L, 5000)).thenReturn(1);

        prepaidService.completeChargePayment(31L, 5000);

        verify(prepaidMapper).applyConfirmedCharge(31L, 5000);
    }

    @Test
    void completeChargeRejectsAmountMismatch() {
        when(prepaidMapper.applyConfirmedCharge(31L, 5000)).thenReturn(-1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> prepaidService.completeChargePayment(31L, 5000)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void useRejectsMissingWashHistory() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> prepaidService.use(new PrepaidUseRequestDTO(), "customer01", "CUSTOMER")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void useMapsInsufficientBalanceToConflict() {
        PrepaidUseRequestDTO request = useRequest(71L);
        PrepaidUseResponseDTO response = new PrepaidUseResponseDTO();
        response.setResultCode("INSUFFICIENT_BALANCE");
        when(prepaidMapper.executeMyPrepaidWashPayment("customer01", 71L))
                .thenReturn(response);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> prepaidService.use(request, "customer01", "CUSTOMER")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void useReturnsSuccessfulDatabaseResult() {
        PrepaidUseRequestDTO request = useRequest(71L);
        PrepaidUseResponseDTO response = new PrepaidUseResponseDTO();
        response.setResultCode("OK");
        response.setPaymentHistoryId(41L);
        response.setBalance(3000);
        when(prepaidMapper.executeMyPrepaidWashPayment("customer01", 71L))
                .thenReturn(response);

        PrepaidUseResponseDTO result =
                prepaidService.use(request, "customer01", "CUSTOMER");

        assertSame(response, result);
        assertEquals(41L, result.getPaymentHistoryId());
        assertEquals(3000, result.getBalance());
    }

    private PrepaidUseRequestDTO useRequest(Long washHistoryId) {
        PrepaidUseRequestDTO request = new PrepaidUseRequestDTO();
        request.setWashHistoryId(washHistoryId);
        return request;
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = PrepaidService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(prepaidService, value);
    }
}
