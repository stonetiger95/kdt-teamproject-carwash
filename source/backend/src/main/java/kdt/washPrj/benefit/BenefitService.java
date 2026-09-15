package kdt.washPrj.benefit;

import kdt.washPrj.benefit.dto.BenefitCampaignDTO;
import kdt.washPrj.benefit.dto.BenefitCampaignRequestDTO;
import kdt.washPrj.benefit.dto.BenefitCampaignStatusDTO;
import kdt.washPrj.benefit.dto.BenefitPolicyDTO;
import kdt.washPrj.benefit.dto.BenefitPolicyRequestDTO;
import kdt.washPrj.benefit.dto.CustomerBenefitOverviewDTO;
import kdt.washPrj.benefit.dto.CustomerBenefitSummaryDTO;
import kdt.washPrj.benefit.dto.CustomerBenefitPreferenceRequestDTO;
import kdt.washPrj.benefit.dto.CustomerCouponDTO;
import kdt.washPrj.benefit.dto.CustomerVisitRewardProgressDTO;
import kdt.washPrj.benefit.dto.CouponAudienceDTO;
import kdt.washPrj.benefit.dto.CouponBulkIssueResponseDTO;
import kdt.washPrj.benefit.dto.PaymentBenefitRefundPolicyDTO;
import kdt.washPrj.benefit.dto.WashBenefitQuoteDTO;
import kdt.washPrj.benefit.dto.WashBenefitSelectionRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Set;

@Service
public class BenefitService {
    private static final Set<String> COUPON_STATUSES = Set.of(
            "ALL", "AVAILABLE", "RESERVED", "USED", "EXPIRED", "CANCELED"
    );
    private static final Set<String> COUPON_AUDIENCE_TYPES = Set.of(
            "ALL", "RECENT", "NEW", "LOYAL", "DORMANT"
    );

    private final BenefitMapper benefitMapper;

    public BenefitService(BenefitMapper benefitMapper) {
        this.benefitMapper = benefitMapper;
    }

    @Transactional(readOnly = true)
    public BenefitPolicyDTO getPolicy(String loginId, Long washSiteId) {
        BenefitPolicyDTO policy = benefitMapper.selectPolicy(loginId, washSiteId);
        requireResult(policy == null ? null : policy.getResultCode());
        return policy;
    }

    @Transactional
    public BenefitPolicyDTO savePolicy(
            String loginId,
            Long washSiteId,
            BenefitPolicyRequestDTO request
    ) {
        requireResult(benefitMapper.savePolicy(loginId, washSiteId, request));
        return getPolicy(loginId, washSiteId);
    }

    @Transactional(readOnly = true)
    public List<BenefitCampaignDTO> getCampaigns(String loginId, Long washSiteId) {
        return benefitMapper.selectCampaigns(loginId, washSiteId);
    }

    @Transactional
    public List<BenefitCampaignStatusDTO> getCampaignStatuses(
            String loginId,
            Long washSiteId
    ) {
        benefitMapper.expireCoupons(loginId, washSiteId);
        return benefitMapper.selectCampaignStatuses(loginId, washSiteId);
    }

    @Transactional
    public Long createCampaign(String loginId, BenefitCampaignRequestDTO request) {
        return benefitMapper.saveCampaign(loginId, null, request);
    }

    @Transactional
    public Long updateCampaign(
            String loginId,
            Long campaignId,
            BenefitCampaignRequestDTO request
    ) {
        return benefitMapper.saveCampaign(loginId, campaignId, request);
    }

    @Transactional
    public Long issueCoupon(String loginId, Long campaignId, Long customerId) {
        return benefitMapper.issueCoupon(loginId, campaignId, customerId);
    }

    @Transactional(readOnly = true)
    public List<CouponAudienceDTO> getCouponAudience(
            String loginId,
            Long campaignId,
            String audienceType,
            Integer recentDays,
            Integer loyalDays,
            Integer loyalMinWashes,
            Integer dormantDays,
            Integer limit
    ) {
        String normalizedType = audienceType == null || audienceType.isBlank()
                ? "ALL"
                : audienceType.trim().toUpperCase(Locale.ROOT);
        if (!COUPON_AUDIENCE_TYPES.contains(normalizedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported coupon audience type.");
        }

        int safeRecentDays = requireRange(recentDays, 30, 1, 3650, "recentDays");
        int safeLoyalDays = requireRange(loyalDays, 90, 1, 3650, "loyalDays");
        int safeLoyalMinWashes = requireRange(loyalMinWashes, 3, 2, 100, "loyalMinWashes");
        int safeDormantDays = requireRange(dormantDays, 60, 1, 3650, "dormantDays");
        int safeLimit = requireRange(limit, 500, 1, 500, "limit");

        return benefitMapper.selectCouponAudience(
                loginId,
                campaignId,
                normalizedType,
                safeRecentDays,
                safeLoyalDays,
                safeLoyalMinWashes,
                safeDormantDays,
                safeLimit
        );
    }

    @Transactional
    public CouponBulkIssueResponseDTO issueCoupons(
            String loginId,
            Long campaignId,
            List<Long> customerIds
    ) {
        LinkedHashSet<Long> uniqueCustomerIds = new LinkedHashSet<>(customerIds);
        List<Long> couponIds = new ArrayList<>(uniqueCustomerIds.size());
        for (Long customerId : uniqueCustomerIds) {
            couponIds.add(benefitMapper.issueCoupon(loginId, campaignId, customerId));
        }
        return new CouponBulkIssueResponseDTO(
                uniqueCustomerIds.size(),
                couponIds.size(),
                couponIds
        );
    }

    @Transactional(readOnly = true)
    public List<CustomerBenefitOverviewDTO> getMyOverview(String loginId) {
        return benefitMapper.selectMyOverview(loginId);
    }

    @Transactional(readOnly = true)
    public CustomerBenefitSummaryDTO getMySummary(String loginId) {
        return benefitMapper.selectMySummary(loginId);
    }

    @Transactional(readOnly = true)
    public List<CustomerVisitRewardProgressDTO> getMyVisitRewardProgress(String loginId) {
        return benefitMapper.selectMyVisitRewardProgress(loginId);
    }

    @Transactional
    public void saveMyPreference(
            String loginId,
            Long washSiteId,
            CustomerBenefitPreferenceRequestDTO request
    ) {
        requireResult(benefitMapper.saveMyPreference(
                loginId,
                washSiteId,
                request.getUseAllMileage(),
                request.getAutoUseCoupon()
        ));
    }

    @Transactional
    public List<CustomerCouponDTO> getMyCoupons(
            String loginId,
            Long washSiteId,
            String status
    ) {
        String normalizedStatus = normalizeCouponStatus(status, "AVAILABLE");
        benefitMapper.expireCoupons(loginId, washSiteId);
        return benefitMapper.selectMyCoupons(loginId, washSiteId, normalizedStatus);
    }

    @Transactional
    public int expireCoupons(String loginId, Long washSiteId) {
        Integer expiredCount = benefitMapper.expireCoupons(loginId, washSiteId);
        return expiredCount == null ? 0 : Math.max(expiredCount, 0);
    }

    @Transactional
    public List<BenefitCampaignStatusDTO> getAdminCouponOverview(
            String loginId,
            Long ownerId,
            Long washSiteId,
            String status
    ) {
        String normalizedStatus = normalizeCouponStatus(status, "ALL");
        benefitMapper.expireCoupons(loginId, washSiteId);
        return benefitMapper.selectAdminCouponOverview(
                loginId,
                ownerId,
                washSiteId,
                normalizedStatus
        );
    }

    @Transactional
    public WashBenefitQuoteDTO selectWashBenefit(
            String loginId,
            Long washHistoryId,
            WashBenefitSelectionRequestDTO request
    ) {
        return benefitMapper.selectWashBenefit(
                loginId,
                washHistoryId,
                request.getUseMileage(),
                request.getCustomerCouponId()
        );
    }

    @Transactional(readOnly = true)
    public PaymentBenefitRefundPolicyDTO getRefundPolicy(
            String loginId,
            Long paymentHistoryId
    ) {
        return benefitMapper.selectRefundPolicy(loginId, paymentHistoryId);
    }

    private void requireResult(String resultCode) {
        if ("OK".equals(resultCode)) {
            return;
        }
        if ("FORBIDDEN".equals(resultCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if ("NOT_FOUND".equals(resultCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if ("INVALID_REQUEST".equals(resultCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid benefit settings.");
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Benefit request could not be completed.");
    }

    private String normalizeCouponStatus(String status, String defaultStatus) {
        String normalized = status == null || status.isBlank()
                ? defaultStatus
                : status.trim().toUpperCase(Locale.ROOT);
        if (!COUPON_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported coupon status."
            );
        }
        return normalized;
    }

    private int requireRange(Integer value, int defaultValue, int min, int max, String field) {
        int normalized = value == null ? defaultValue : value;
        if (normalized < min || normalized > max) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    field + " must be between " + min + " and " + max + "."
            );
        }
        return normalized;
    }
}
