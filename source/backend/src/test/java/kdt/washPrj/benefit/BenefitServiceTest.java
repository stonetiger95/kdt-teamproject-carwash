package kdt.washPrj.benefit;

import kdt.washPrj.benefit.dto.BenefitCampaignStatusDTO;
import kdt.washPrj.benefit.dto.CustomerCouponDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenefitServiceTest {

    @Mock
    private BenefitMapper benefitMapper;

    private BenefitService benefitService;

    @BeforeEach
    void setUp() {
        benefitService = new BenefitService(benefitMapper);
    }

    @Test
    void campaignStatusExpiresCouponsBeforeAggregation() {
        BenefitCampaignStatusDTO status = new BenefitCampaignStatusDTO();
        status.setCouponCampaignId(10L);
        when(benefitMapper.selectCampaignStatuses("owner01", 1L))
                .thenReturn(List.of(status));

        List<BenefitCampaignStatusDTO> result =
                benefitService.getCampaignStatuses("owner01", 1L);

        assertEquals(10L, result.get(0).getCouponCampaignId());
        var order = inOrder(benefitMapper);
        order.verify(benefitMapper).expireCoupons("owner01", 1L);
        order.verify(benefitMapper).selectCampaignStatuses("owner01", 1L);
    }

    @Test
    void customerCouponStatusIsNormalized() {
        CustomerCouponDTO coupon = new CustomerCouponDTO();
        coupon.setStatus("USED");
        when(benefitMapper.selectMyCoupons("user01", 1L, "USED"))
                .thenReturn(List.of(coupon));

        List<CustomerCouponDTO> result =
                benefitService.getMyCoupons("user01", 1L, " used ");

        assertEquals("USED", result.get(0).getStatus());
        verify(benefitMapper).expireCoupons("user01", 1L);
        verify(benefitMapper).selectMyCoupons("user01", 1L, "USED");
    }

    @Test
    void customerCouponRejectsUnknownStatusBeforeDatabaseCall() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> benefitService.getMyCoupons("user01", 1L, "BROKEN")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoMoreInteractions(benefitMapper);
    }

    @Test
    void adminOverviewUsesAllAsDefaultStatus() {
        benefitService.getAdminCouponOverview("admin01", null, null, null);

        verify(benefitMapper).expireCoupons("admin01", null);
        verify(benefitMapper).selectAdminCouponOverview(
                "admin01", null, null, "ALL"
        );
    }

    @Test
    void explicitExpiryReturnsZeroWhenDatabaseReturnsNull() {
        when(benefitMapper.expireCoupons("admin01", null)).thenReturn(null);

        assertEquals(0, benefitService.expireCoupons("admin01", null));
    }
}
