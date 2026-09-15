package kdt.washPrj.settlement;

import kdt.washPrj.settlement.dto.SettlementSiteComparisonDTO;
import kdt.washPrj.settlement.dto.SettlementSummaryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettlementServiceTest {

    private SettlementMapper mapper;
    private SettlementService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SettlementMapper.class);
        service = new SettlementService(mapper, JsonMapper.builder().build());
    }

    @Test
    void ownerDashboardNormalizesPeriodAndReadsBundle() {
        when(mapper.selectDashboardBundle(
                "owner01",
                null,
                7L,
                "DAILY",
                "2026-07-01",
                "2026-07-31"
        )).thenReturn("{\"nonMemberUseCount\":4,\"conversionCount\":1,\"conversionRate\":25.0}");

        SettlementSummaryDTO result = service.getOwnerDashboard(
                "owner01",
                "invalid",
                "2026-07-01",
                "2026-07-31",
                7L
        );

        assertEquals("DAILY", result.getPeriodType());
        assertEquals(4L, result.getNonMemberUseCount());
        assertEquals(25.0, result.getConversionRate());
    }

    @Test
    void adminDashboardSwapsDatesAndNormalizesEmptyBundle() {
        when(mapper.selectDashboardBundle(
                "admin01",
                3L,
                null,
                "MONTHLY",
                "2026-07-01",
                "2026-07-31"
        )).thenReturn(null);

        SettlementSummaryDTO result = service.getAdminDashboard(
                "admin01",
                "MONTHLY",
                "2026-07-31",
                "2026-07-01",
                3L,
                null
        );

        assertEquals("2026-07-01", result.getStartDate());
        assertEquals("2026-07-31", result.getEndDate());
        assertEquals(0L, result.getTotalUseCount());
        assertEquals(0.0, result.getConversionRate());
    }

    @Test
    void siteComparisonSwapsReversedDatesAndPassesAdminIdentity() {
        List<SettlementSiteComparisonDTO> comparisons =
                List.of(new SettlementSiteComparisonDTO());
        when(mapper.selectSiteComparisons(
                "admin01",
                3L,
                7L,
                "2026-07-01",
                "2026-07-31"
        )).thenReturn(comparisons);

        List<SettlementSiteComparisonDTO> result =
                service.getAdminSiteComparisons(
                        "admin01",
                        "2026-07-31",
                        "2026-07-01",
                        3L,
                        7L
                );

        assertSame(comparisons, result);
        verify(mapper).selectSiteComparisons(
                "admin01",
                3L,
                7L,
                "2026-07-01",
                "2026-07-31"
        );
    }
}
