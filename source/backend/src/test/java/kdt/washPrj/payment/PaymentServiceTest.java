package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentResponseDTO;
import kdt.washPrj.payment.dto.PaymentCancelDTO;
import kdt.washPrj.payment.dto.PaymentProviderResultDTO;
import kdt.washPrj.payment.dto.PaymentRefundPreparationDTO;
import kdt.washPrj.payment.dto.PaymentRequestPreparationDTO;
import kdt.washPrj.payment.dto.PaymentWaitingDTO;
import kdt.washPrj.payment.dto.PaymentWaitingResponseDTO;
import kdt.washPrj.payment.dto.RefundReasonCode;
import kdt.washPrj.prepaid.PrepaidService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

class PaymentServiceTest {

    private PaymentQueryMapper paymentQueryMapper;
    private PaymentCommandMapper paymentCommandMapper;
    private PrepaidService prepaidService;
    private PaymentQueryService paymentQueryService;
    private PayAppClient payAppClient;
    private PaymentProviderRegistry paymentProviderRegistry;
    private PaymentReconciliationService paymentReconciliationService;
    private KakaoPayService kakaoPayService;
    private TossPaymentsService tossPaymentsService;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() throws Exception {
        paymentQueryMapper = mock(PaymentQueryMapper.class);
        paymentCommandMapper = mock(PaymentCommandMapper.class);
        prepaidService = mock(PrepaidService.class);
        paymentQueryService = mock(PaymentQueryService.class);
        payAppClient = mock(PayAppClient.class);
        paymentProviderRegistry = mock(PaymentProviderRegistry.class);
        paymentReconciliationService = mock(PaymentReconciliationService.class);
        kakaoPayService = mock(KakaoPayService.class);
        tossPaymentsService = mock(TossPaymentsService.class);
        paymentService = new PaymentService(
                paymentQueryMapper,
                paymentCommandMapper,
                prepaidService,
                paymentQueryService,
                payAppClient,
                paymentProviderRegistry,
                paymentReconciliationService,
                kakaoPayService,
                tossPaymentsService
        );

        setField("payAppUserid", "test-user");
        setField("payAppLinkKey", "test-key");
        setField("payAppLinkValue", "test-value");
        when(paymentProviderRegistry.requireAvailable(any()))
                .thenReturn("PAYAPP");
    }

    @Test
    void providerNeutralCallbackStopsAfterAtomicDbApplication() {
        PaymentProviderResultDTO providerResult = new PaymentProviderResultDTO();
        providerResult.setResultCode("APPLIED");
        providerResult.setPurpose("WASH_PAYMENT");
        providerResult.setWashHistoryId(205L);
        providerResult.setPreviousStatus("WAITING");
        providerResult.setCurrentStatus("SUCCESS");

        when(paymentCommandMapper.applyProviderPaymentResult(
                1329L,
                "PAYAPP",
                "PAYAPP-123",
                "4",
                "CARD",
                7000,
                "SUCCESS",
                LocalDateTime.of(2026, 7, 15, 12, 0),
                null
        )).thenReturn(providerResult);

        String result = paymentService.handlePayAppFeedback(feedbackParams("205"));

        assertEquals("SUCCESS", result);
        verify(paymentQueryMapper, never())
                .selectNonMemberPaymentForFeedback(anyLong(), anyLong());
        verify(paymentCommandMapper, never()).applyNonMemberPaymentFeedbackAndWash(
                anyLong(), anyLong(), any(), anyInt(),
                anyString(), any(), any(), any()
        );
    }

    @Test
    void nonMemberFeedbackUsesOnlyNonMemberPaymentUpdate() {
        PaymentResponseDTO payment = new PaymentResponseDTO();
        payment.setPaymentId(1329L);
        payment.setWashHistoryId(205L);
        payment.setAmount(7000);

        when(paymentQueryMapper.selectNonMemberPaymentForFeedback(1329L, 205L))
                .thenReturn(payment);
        when(paymentCommandMapper.applyNonMemberPaymentFeedbackAndWash(
                1329L,
                205L,
                "CARD",
                7000,
                "SUCCESS",
                LocalDateTime.of(2026, 7, 15, 12, 0),
                "PAYAPP-123",
                null
        )).thenReturn(1);

        String result = paymentService.handlePayAppFeedback(
                feedbackParams("205")
        );

        assertEquals("SUCCESS", result);
        verify(paymentCommandMapper).applyNonMemberPaymentFeedbackAndWash(
                1329L,
                205L,
                "CARD",
                7000,
                "SUCCESS",
                LocalDateTime.of(2026, 7, 15, 12, 0),
                "PAYAPP-123",
                null
        );
        verify(paymentQueryMapper, never()).selectPaymentById(anyLong());
        verify(paymentCommandMapper, never()).applyPaymentFeedbackAndWash(
                anyLong(),
                any(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any()
        );
        verifyNoInteractions(prepaidService);
    }

    @Test
    void customerFeedbackKeepsExistingSharedPaymentUpdate() {
        PaymentResponseDTO payment = new PaymentResponseDTO();
        payment.setPaymentId(1329L);
        payment.setAmount(7000);

        when(paymentQueryMapper.selectPaymentByIdForUpdate(1329L))
                .thenReturn(payment);
        when(paymentCommandMapper.applyPaymentFeedbackAndWash(
                1329L,
                "CARD",
                7000,
                "SUCCESS",
                LocalDateTime.of(2026, 7, 15, 12, 0),
                "PAYAPP-123",
                null
        )).thenReturn(1);

        String result = paymentService.handlePayAppFeedback(
                feedbackParams(null)
        );

        assertEquals("SUCCESS", result);
        verify(paymentCommandMapper).applyPaymentFeedbackAndWash(
                1329L,
                "CARD",
                7000,
                "SUCCESS",
                LocalDateTime.of(2026, 7, 15, 12, 0),
                "PAYAPP-123",
                null
        );
        verify(paymentQueryMapper, never())
                .selectNonMemberPaymentForFeedback(anyLong(), anyLong());
        verify(paymentCommandMapper, never()).applyNonMemberPaymentFeedbackAndWash(
                anyLong(),
                anyLong(),
                any(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any()
        );
        verifyNoInteractions(prepaidService);
    }

    @Test
    void duplicateCustomerSuccessFeedbackDoesNotUpdateAgain() {
        PaymentResponseDTO payment = new PaymentResponseDTO();
        payment.setPaymentId(1329L);
        payment.setAmount(7000);
        payment.setStatus("SUCCESS");

        when(paymentQueryMapper.selectPaymentByIdForUpdate(1329L))
                .thenReturn(payment);

        String result = paymentService.handlePayAppFeedback(
                feedbackParams(null)
        );

        assertEquals("SUCCESS", result);
        verify(paymentCommandMapper, never()).applyPaymentFeedbackAndWash(
                anyLong(), any(), anyInt(), anyString(), any(), any(), any()
        );
        verifyNoInteractions(prepaidService);
    }

    @Test
    void refundAmountCannotExceedRemainingAmount() {
        PaymentRefundPreparationDTO preparation = new PaymentRefundPreparationDTO();
        preparation.setResultCode("INVALID_AMOUNT");
        when(paymentCommandMapper.preparePaymentRefund(
                "admin01", 200L, 8000, "Partial refund test", null
        )).thenReturn(preparation);

        PaymentCancelDTO request = new PaymentCancelDTO();
        request.setRefundAmount(8000);
        request.setCancelReason("Partial refund test");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.cancelPayment(
                        200L,
                        request,
                        "admin01",
                        "ROLE_ADMIN"
                )
        );

        assertEquals(409, error.getStatusCode().value());
        verify(paymentCommandMapper, never()).completePaymentRefund(anyLong(), any());
        verifyNoInteractions(payAppClient);
    }

    @Test
    void partialCancellationFeedbackOnlyConfirmsKnownRefund() {
        Map<String, String> params = feedbackParams(null);
        params.put("pay_state", "70");
        params.put("price", "10000");
        params.put("cancelprice", "3000");

        when(paymentQueryMapper.existsSuccessfulRefund(1329L, 3000))
                .thenReturn(true);

        String result = paymentService.handlePayAppFeedback(params);

        assertEquals("SUCCESS", result);
        verify(paymentQueryMapper).existsSuccessfulRefund(1329L, 3000);
        verify(paymentCommandMapper, never()).applyPaymentFeedbackAndWash(
                anyLong(),
                any(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void duplicateInquiryRefundIsRejectedBeforeRefundExecution() {
        PaymentRefundPreparationDTO preparation = new PaymentRefundPreparationDTO();
        preparation.setResultCode("DUPLICATE_INQUIRY");
        when(paymentCommandMapper.preparePaymentRefund(
                "admin01", 300L, 3000, "Duplicate request test", 77L
        )).thenReturn(preparation);

        PaymentCancelDTO request = new PaymentCancelDTO();
        request.setRefundAmount(3000);
        request.setCancelReason("Duplicate request test");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.refundPaymentForInquiry(
                        300L,
                        request,
                        "admin01",
                        "ROLE_ADMIN",
                        77L,
                        "Refund completed."
                )
        );

        assertEquals(409, error.getStatusCode().value());
        verify(paymentCommandMapper, never()).completePaymentRefund(anyLong(), any());
        verifyNoInteractions(payAppClient);
    }

    @Test
    void providerSuccessIsReservedWhenLocalRefundSyncFails() {
        PaymentRefundPreparationDTO preparation = new PaymentRefundPreparationDTO();
        preparation.setResultCode("OK");
        preparation.setRefundHistoryId(900L);
        preparation.setPaymentHistoryId(400L);
        preparation.setRefundAmount(10000);
        preparation.setOriginalAmount(10000);
        preparation.setRefundType("PAYAPP");
        preparation.setPayappPaymentId("PAYAPP-400");
        when(paymentCommandMapper.preparePaymentRefund(
                "admin01", 400L, 10000, "Provider success test", null
        )).thenReturn(preparation);
        when(payAppClient.postForm(any()))
                .thenReturn(Map.of("state", "1"));

        PaymentCancelDTO request = new PaymentCancelDTO();
        request.setRefundAmount(10000);
        request.setCancelReason("Provider success test");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.cancelPayment(
                        400L,
                        request,
                        "admin01",
                        "ROLE_ADMIN"
                )
        );

        assertEquals(409, error.getStatusCode().value());
        verify(paymentCommandMapper).markRefundReconciliationRequired(
                900L,
                "state=1;errorMessage=;localSync=PENDING"
        );
    }

    @Test
    void nonMemberCanceledFeedbackBeforeSuccessDoesNotCreatePayment() {
        Map<String, String> params = feedbackParams("205");
        params.put("pay_state", "32");
        params.put("canceldate", "2026-07-15 12:05:00");

        when(paymentQueryMapper.selectNonMemberPaymentForFeedback(1329L, 205L))
                .thenReturn(null);

        String result = paymentService.handlePayAppFeedback(params);

        assertEquals("SUCCESS", result);
        verify(paymentCommandMapper, never()).insertSuccessfulPaymentFromFeedback(
                anyLong(), any(), any(), anyInt(), any(), any(), any(), any()
        );
        verify(paymentCommandMapper, never()).applyNonMemberPaymentFeedbackAndWash(
                anyLong(), anyLong(), any(), anyInt(), anyString(), any(), any(), any()
        );
    }

    @Test
    void nonMemberCancellationUpdatesPaymentAndWashStatus() {
        PaymentResponseDTO payment = new PaymentResponseDTO();
        payment.setPaymentId(1329L);
        payment.setWashHistoryId(205L);
        payment.setAmount(7000);
        payment.setStatus("SUCCESS");

        when(paymentQueryMapper.selectNonMemberPaymentForFeedback(1329L, 205L))
                .thenReturn(payment);
        when(paymentCommandMapper.applyNonMemberPaymentFeedbackAndWash(
                1329L,
                205L,
                "CARD",
                7000,
                "CANCELED",
                LocalDateTime.of(2026, 7, 15, 12, 5),
                "PAYAPP-123",
                "Customer canceled"
        )).thenReturn(1);

        Map<String, String> params = feedbackParams("205");
        params.put("pay_state", "32");
        params.put("canceldate", "2026-07-15 12:05:00");
        params.put("cancelmemo", "Customer canceled");

        String result = paymentService.handlePayAppFeedback(params);

        assertEquals("SUCCESS", result);
        verify(paymentCommandMapper).applyNonMemberPaymentFeedbackAndWash(
                1329L,
                205L,
                "CARD",
                7000,
                "CANCELED",
                LocalDateTime.of(2026, 7, 15, 12, 5),
                "PAYAPP-123",
                "Customer canceled"
        );
    }

    @Test
    void duplicateNonMemberSuccessFeedbackDoesNotUpdateAgain() {
        PaymentResponseDTO payment = new PaymentResponseDTO();
        payment.setPaymentId(1329L);
        payment.setWashHistoryId(205L);
        payment.setAmount(7000);
        payment.setStatus("SUCCESS");

        when(paymentQueryMapper.selectNonMemberPaymentForFeedback(1329L, 205L))
                .thenReturn(payment);

        String result = paymentService.handlePayAppFeedback(feedbackParams("205"));

        assertEquals("SUCCESS", result);
        verify(paymentCommandMapper, never()).applyNonMemberPaymentFeedbackAndWash(
                anyLong(), anyLong(), any(), anyInt(),
                anyString(), any(), any(), any()
        );
    }

    @Test
    void nonMemberPaymentUsesCheckoutDefaultBeforePayAppRequest() {
        PaymentWaitingDTO request = nonMemberPaymentRequest();
        PaymentRequestPreparationDTO preparation = nonMemberPreparation();

        when(paymentCommandMapper.prepareNonMemberPaymentRequest(205L, "PAYAPP"))
                .thenReturn(preparation);
        when(payAppClient.postForm(any()))
                .thenReturn(Map.of(
                        "state", "1",
                        "payurl", "https://pay.example/1329",
                        "qrurl", "https://pay.example/1329/qr"
                ));

        PaymentWaitingResponseDTO result =
                paymentService.waitingNonMemberPayment(request);

        assertEquals(1329L, result.getPaymentId());
        assertEquals("Basic wash", result.getOrderName());
        assertEquals(7000, result.getAmount());
        verify(paymentCommandMapper).prepareNonMemberPaymentRequest(205L, "PAYAPP");
    }

    @Test
    void nonMemberPayAppRequestFailureReturnsBadGateway() {
        PaymentWaitingDTO request = nonMemberPaymentRequest();

        when(paymentCommandMapper.prepareNonMemberPaymentRequest(205L, "PAYAPP"))
                .thenReturn(nonMemberPreparation());
        when(payAppClient.postForm(any()))
                .thenReturn(Map.of(
                        "state", "0",
                        "errorMessage", "Provider rejected request"
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.waitingNonMemberPayment(request)
        );

        assertEquals(502, exception.getStatusCode().value());
        verify(paymentCommandMapper).prepareNonMemberPaymentRequest(205L, "PAYAPP");
        verify(paymentCommandMapper).markPaymentRequestFailed(
                1329L,
                "PAYAPP",
                "0",
                "Provider rejected request"
        );
    }

    @Test
    void policyViolationRefundStoresStructuredReasonCode() {
        PaymentRefundPreparationDTO preparation = new PaymentRefundPreparationDTO();
        preparation.setResultCode("INVALID_STATUS");
        when(paymentCommandMapper.preparePaymentRefund(
                "admin01",
                201L,
                10000,
                "[POLICY_VIOLATION] Coupon abuse",
                null
        )).thenReturn(preparation);

        PaymentCancelDTO request = new PaymentCancelDTO();
        request.setRefundAmount(10000);
        request.setCancelReason("Coupon abuse");
        request.setRefundReasonCode(RefundReasonCode.POLICY_VIOLATION);

        assertThrows(
                ResponseStatusException.class,
                () -> paymentService.cancelPayment(
                        201L,
                        request,
                        "admin01",
                        "ROLE_ADMIN"
                )
        );

        verify(paymentCommandMapper).preparePaymentRefund(
                "admin01",
                201L,
                10000,
                "[POLICY_VIOLATION] Coupon abuse",
                null
        );
    }

    private PaymentWaitingDTO nonMemberPaymentRequest() {
        PaymentWaitingDTO request = new PaymentWaitingDTO();
        request.setWashHistoryId(205L);
        request.setOrderName("Basic wash");
        request.setAmount(7000);
        request.setRecvPhone("01012345678");
        return request;
    }

    private PaymentRequestPreparationDTO nonMemberPreparation() {
        PaymentRequestPreparationDTO preparation =
                new PaymentRequestPreparationDTO();
        preparation.setResultCode("OK");
        preparation.setPaymentHistoryId(1329L);
        preparation.setWashHistoryId(205L);
        preparation.setOrderName("Basic wash");
        preparation.setAmount(7000);
        return preparation;
    }

    private Map<String, String> feedbackParams(String washHistoryId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("userid", "test-user");
        params.put("linkkey", "test-key");
        params.put("linkval", "test-value");
        params.put("var1", "1329");

        if (washHistoryId != null) {
            params.put("var2", washHistoryId);
        }

        params.put("price", "7000");
        params.put("pay_state", "4");
        params.put("pay_type", "1");
        params.put("pay_date", "2026-07-15 12:00:00");
        params.put("mul_no", "PAYAPP-123");
        return params;
    }

    private void setField(String name, String value) throws Exception {
        Field field = PaymentService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(paymentService, value);
    }
}
