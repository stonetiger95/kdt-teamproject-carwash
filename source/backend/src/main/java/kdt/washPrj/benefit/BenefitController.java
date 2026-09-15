package kdt.washPrj.benefit;

import jakarta.validation.Valid;
import kdt.washPrj.benefit.dto.BenefitCampaignDTO;
import kdt.washPrj.benefit.dto.BenefitCampaignRequestDTO;
import kdt.washPrj.benefit.dto.BenefitCampaignStatusDTO;
import kdt.washPrj.benefit.dto.BenefitPolicyDTO;
import kdt.washPrj.benefit.dto.BenefitPolicyRequestDTO;
import kdt.washPrj.benefit.dto.CouponIssueRequestDTO;
import kdt.washPrj.benefit.dto.CouponBulkIssueRequestDTO;
import kdt.washPrj.benefit.dto.CouponBulkIssueResponseDTO;
import kdt.washPrj.benefit.dto.CustomerBenefitOverviewDTO;
import kdt.washPrj.benefit.dto.CustomerBenefitSummaryDTO;
import kdt.washPrj.benefit.dto.CustomerBenefitPreferenceRequestDTO;
import kdt.washPrj.benefit.dto.CustomerCouponDTO;
import kdt.washPrj.benefit.dto.CustomerVisitRewardProgressDTO;
import kdt.washPrj.benefit.dto.CouponAudienceDTO;
import kdt.washPrj.benefit.dto.PaymentBenefitRefundPolicyDTO;
import kdt.washPrj.benefit.dto.WashBenefitQuoteDTO;
import kdt.washPrj.benefit.dto.WashBenefitSelectionRequestDTO;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BenefitController {
    private final BenefitService benefitService;

    public BenefitController(BenefitService benefitService) {
        this.benefitService = benefitService;
    }

    @GetMapping({
            "/api/owner/benefits/sites/{washSiteId}/policy",
            "/api/admin/benefits/sites/{washSiteId}/policy"
    })
    public BenefitPolicyDTO getPolicy(
            Authentication authentication,
            @PathVariable Long washSiteId
    ) {
        return benefitService.getPolicy(authentication.getName(), washSiteId);
    }

    @PutMapping({
            "/api/owner/benefits/sites/{washSiteId}/policy",
            "/api/admin/benefits/sites/{washSiteId}/policy"
    })
    public BenefitPolicyDTO savePolicy(
            Authentication authentication,
            @PathVariable Long washSiteId,
            @Valid @RequestBody BenefitPolicyRequestDTO request
    ) {
        return benefitService.savePolicy(authentication.getName(), washSiteId, request);
    }

    @GetMapping({
            "/api/owner/benefits/sites/{washSiteId}/campaigns",
            "/api/admin/benefits/sites/{washSiteId}/campaigns"
    })
    public List<BenefitCampaignDTO> getCampaigns(
            Authentication authentication,
            @PathVariable Long washSiteId
    ) {
        return benefitService.getCampaigns(authentication.getName(), washSiteId);
    }

    @GetMapping({
            "/api/owner/benefits/sites/{washSiteId}/campaign-status",
            "/api/admin/benefits/sites/{washSiteId}/campaign-status"
    })
    public List<BenefitCampaignStatusDTO> getCampaignStatuses(
            Authentication authentication,
            @PathVariable Long washSiteId
    ) {
        return benefitService.getCampaignStatuses(authentication.getName(), washSiteId);
    }

    @PostMapping({"/api/owner/benefits/campaigns", "/api/admin/benefits/campaigns"})
    public Long createCampaign(
            Authentication authentication,
            @Valid @RequestBody BenefitCampaignRequestDTO request
    ) {
        return benefitService.createCampaign(authentication.getName(), request);
    }

    @PutMapping({
            "/api/owner/benefits/campaigns/{campaignId}",
            "/api/admin/benefits/campaigns/{campaignId}"
    })
    public Long updateCampaign(
            Authentication authentication,
            @PathVariable Long campaignId,
            @Valid @RequestBody BenefitCampaignRequestDTO request
    ) {
        return benefitService.updateCampaign(authentication.getName(), campaignId, request);
    }

    @PostMapping({
            "/api/owner/benefits/campaigns/{campaignId}/coupons",
            "/api/admin/benefits/campaigns/{campaignId}/coupons"
    })
    public Long issueCoupon(
            Authentication authentication,
            @PathVariable Long campaignId,
            @Valid @RequestBody CouponIssueRequestDTO request
    ) {
        return benefitService.issueCoupon(
                authentication.getName(),
                campaignId,
                request.getCustomerId()
        );
    }

    @GetMapping({
            "/api/owner/benefits/campaigns/{campaignId}/audience",
            "/api/admin/benefits/campaigns/{campaignId}/audience"
    })
    public List<CouponAudienceDTO> getCouponAudience(
            Authentication authentication,
            @PathVariable Long campaignId,
            @RequestParam(defaultValue = "ALL") String audienceType,
            @RequestParam(defaultValue = "30") Integer recentDays,
            @RequestParam(defaultValue = "90") Integer loyalDays,
            @RequestParam(defaultValue = "3") Integer loyalMinWashes,
            @RequestParam(defaultValue = "60") Integer dormantDays,
            @RequestParam(defaultValue = "500") Integer limit
    ) {
        return benefitService.getCouponAudience(
                authentication.getName(),
                campaignId,
                audienceType,
                recentDays,
                loyalDays,
                loyalMinWashes,
                dormantDays,
                limit
        );
    }

    @PostMapping({
            "/api/owner/benefits/campaigns/{campaignId}/coupons/bulk",
            "/api/admin/benefits/campaigns/{campaignId}/coupons/bulk"
    })
    public CouponBulkIssueResponseDTO issueCoupons(
            Authentication authentication,
            @PathVariable Long campaignId,
            @Valid @RequestBody CouponBulkIssueRequestDTO request
    ) {
        return benefitService.issueCoupons(
                authentication.getName(),
                campaignId,
                request.getCustomerIds()
        );
    }

    @GetMapping("/api/customer/benefits")
    public List<CustomerBenefitOverviewDTO> getMyOverview(Authentication authentication) {
        return benefitService.getMyOverview(authentication.getName());
    }

    @GetMapping("/api/customer/benefits/summary")
    public CustomerBenefitSummaryDTO getMySummary(Authentication authentication) {
        return benefitService.getMySummary(authentication.getName());
    }

    @GetMapping("/api/customer/benefits/visit-progress")
    public List<CustomerVisitRewardProgressDTO> getMyVisitRewardProgress(
            Authentication authentication
    ) {
        return benefitService.getMyVisitRewardProgress(authentication.getName());
    }

    @PatchMapping("/api/customer/benefits/sites/{washSiteId}/preference")
    public void saveMyPreference(
            Authentication authentication,
            @PathVariable Long washSiteId,
            @Valid @RequestBody CustomerBenefitPreferenceRequestDTO request
    ) {
        benefitService.saveMyPreference(authentication.getName(), washSiteId, request);
    }

    @GetMapping("/api/customer/benefits/sites/{washSiteId}/coupons")
    public List<CustomerCouponDTO> getMyCoupons(
            Authentication authentication,
            @PathVariable Long washSiteId,
            @RequestParam(defaultValue = "AVAILABLE") String status
    ) {
        return benefitService.getMyCoupons(authentication.getName(), washSiteId, status);
    }

    @PostMapping({
            "/api/owner/benefits/coupons/expire",
            "/api/admin/benefits/coupons/expire"
    })
    public int expireCoupons(
            Authentication authentication,
            @RequestParam(required = false) Long washSiteId
    ) {
        return benefitService.expireCoupons(authentication.getName(), washSiteId);
    }

    @GetMapping("/api/admin/benefits/coupons/overview")
    public List<BenefitCampaignStatusDTO> getAdminCouponOverview(
            Authentication authentication,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long washSiteId,
            @RequestParam(defaultValue = "ALL") String status
    ) {
        return benefitService.getAdminCouponOverview(
                authentication.getName(),
                ownerId,
                washSiteId,
                status
        );
    }

    @PutMapping("/api/customer/benefits/wash-history/{washHistoryId}/selection")
    public WashBenefitQuoteDTO selectWashBenefit(
            Authentication authentication,
            @PathVariable Long washHistoryId,
            @Valid @RequestBody WashBenefitSelectionRequestDTO request
    ) {
        return benefitService.selectWashBenefit(
                authentication.getName(),
                washHistoryId,
                request
        );
    }

    @GetMapping({
            "/api/customer/benefits/payments/{paymentHistoryId}/refund-policy",
            "/api/owner/benefits/payments/{paymentHistoryId}/refund-policy",
            "/api/admin/benefits/payments/{paymentHistoryId}/refund-policy"
    })
    public PaymentBenefitRefundPolicyDTO getRefundPolicy(
            Authentication authentication,
            @PathVariable Long paymentHistoryId
    ) {
        return benefitService.getRefundPolicy(authentication.getName(), paymentHistoryId);
    }
}
