package kdt.washPrj.prepaid;

import kdt.washPrj.prepaid.dto.BillingCardDTO;
import kdt.washPrj.prepaid.dto.PrepaidWalletDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrepaidQueryServiceTest {

    private PrepaidMapper prepaidMapper;
    private PrepaidQueryService service;

    @BeforeEach
    void setUp() {
        prepaidMapper = mock(PrepaidMapper.class);
        service = new PrepaidQueryService(prepaidMapper);
    }

    @Test
    void getWalletReturnsMapperResult() {
        PrepaidWalletDTO expected = new PrepaidWalletDTO();
        when(prepaidMapper.selectAuthorizedWallet("customer01"))
                .thenReturn(expected);

        PrepaidWalletDTO actual = service.getWallet("customer01");

        assertSame(expected, actual);
        verify(prepaidMapper).selectAuthorizedWallet("customer01");
    }

    @Test
    void getBillingCardsReturnsCustomerCards() {
        List<BillingCardDTO> expected = List.of(new BillingCardDTO());
        when(prepaidMapper.selectAuthorizedBillingCards("customer01"))
                .thenReturn(expected);

        List<BillingCardDTO> actual =
                service.getBillingCards("customer01", "ROLE_CUSTOMER");

        assertSame(expected, actual);
        verify(prepaidMapper).selectAuthorizedBillingCards("customer01");
    }
}
