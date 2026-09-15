package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentResponseDTO;
import kdt.washPrj.payment.dto.PaymentSearchCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentQueryServiceTest {

    private PaymentQueryMapper mapper;
    private PaymentQueryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PaymentQueryMapper.class);
        service = new PaymentQueryService(mapper);
    }

    @Test
    void getPaymentListReturnsAdminResults() {
        PaymentSearchCondition condition = new PaymentSearchCondition();
        List<PaymentResponseDTO> expected = List.of(new PaymentResponseDTO());
        when(mapper.selectAuthorizedPaymentList("admin01", condition))
                .thenReturn(expected);

        List<PaymentResponseDTO> actual =
                service.getPaymentList("admin01", "ROLE_ADMIN", condition);

        assertSame(expected, actual);
        verify(mapper).selectAuthorizedPaymentList("admin01", condition);
    }

    @Test
    void getPaymentReturnsAdminVisiblePayment() {
        PaymentResponseDTO expected = new PaymentResponseDTO();
        expected.setPaymentId(19L);
        when(mapper.selectAuthorizedPaymentById("admin01", 19L))
                .thenReturn(expected);

        PaymentResponseDTO actual =
                service.getPayment(19L, "admin01", "ROLE_ADMIN");

        assertSame(expected, actual);
    }
}
