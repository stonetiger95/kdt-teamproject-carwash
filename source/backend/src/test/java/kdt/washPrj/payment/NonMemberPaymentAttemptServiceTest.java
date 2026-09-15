package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentWaitingDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NonMemberPaymentAttemptServiceTest {

    private PaymentQueryMapper paymentQueryMapper;
    private PaymentCommandMapper paymentCommandMapper;
    private NonMemberPaymentAttemptService service;

    @BeforeEach
    void setUp() {
        paymentQueryMapper = mock(PaymentQueryMapper.class);
        paymentCommandMapper = mock(PaymentCommandMapper.class);
        service = new NonMemberPaymentAttemptService(
                paymentQueryMapper,
                paymentCommandMapper
        );
    }

    @Test
    void reservesWaitingPaymentBeforePayAppRequest() {
        PaymentWaitingDTO checkoutDefault = checkoutDefault();

        when(paymentQueryMapper.lockNonMemberWashHistory(205L)).thenReturn(205L);
        when(paymentQueryMapper.existsNonMemberWaitingPayment(205L)).thenReturn(false);
        when(paymentQueryMapper.selectNonMemberCheckoutDefault(205L))
                .thenReturn(checkoutDefault);
        when(paymentCommandMapper.reservePaymentHistoryId()).thenReturn(1329L);
        when(paymentCommandMapper.insertNonMemberPaymentWaiting(1329L, 205L, 7000))
                .thenReturn(1);

        NonMemberPaymentAttemptService.Reservation result =
                service.reserve(205L);

        assertEquals(1329L, result.paymentHistoryId());
        assertEquals(checkoutDefault, result.checkoutDefault());
        verify(paymentCommandMapper)
                .insertNonMemberPaymentWaiting(1329L, 205L, 7000);
    }

    @Test
    void rejectsSecondWaitingPaymentForSameWashHistory() {
        when(paymentQueryMapper.lockNonMemberWashHistory(205L)).thenReturn(205L);
        when(paymentQueryMapper.existsNonMemberWaitingPayment(205L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.reserve(205L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(paymentCommandMapper, never()).reservePaymentHistoryId();
    }

    @Test
    void rejectsMissingNonMemberWashHistory() {
        when(paymentQueryMapper.lockNonMemberWashHistory(205L))
                .thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.reserve(205L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(paymentCommandMapper, never()).reservePaymentHistoryId();
    }

    private PaymentWaitingDTO checkoutDefault() {
        PaymentWaitingDTO checkoutDefault = new PaymentWaitingDTO();
        checkoutDefault.setWashHistoryId(205L);
        checkoutDefault.setOrderName("Basic wash");
        checkoutDefault.setAmount(7000);
        return checkoutDefault;
    }
}
