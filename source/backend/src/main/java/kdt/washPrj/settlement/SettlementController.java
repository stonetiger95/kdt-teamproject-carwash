package kdt.washPrj.settlement;

import kdt.washPrj.settlement.dto.SettlementSiteComparisonDTO;
import kdt.washPrj.settlement.dto.SettlementSummaryDTO;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/owner/settlement/dashboard")
    public SettlementSummaryDTO ownerSettlementDashboard(
            Authentication authentication,
            @RequestParam(defaultValue = "DAILY") String periodType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long washSiteId
    ) {
        return settlementService.getOwnerDashboard(
                authentication.getName(),
                periodType,
                startDate,
                endDate,
                washSiteId
        );
    }

    @GetMapping("/admin/settlement/dashboard")
    public SettlementSummaryDTO adminSettlementDashboard(
            Authentication authentication,
            @RequestParam(defaultValue = "DAILY") String periodType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long washSiteId
    ) {
        return settlementService.getAdminDashboard(
                authentication.getName(),
                periodType,
                startDate,
                endDate,
                ownerId,
                washSiteId
        );
    }

    @GetMapping("/admin/settlement/site-comparisons")
    public List<SettlementSiteComparisonDTO> adminSiteComparisons(
            Authentication authentication,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long washSiteId
    ) {
        return settlementService.getAdminSiteComparisons(
                authentication.getName(),
                startDate,
                endDate,
                ownerId,
                washSiteId
        );
    }
}
