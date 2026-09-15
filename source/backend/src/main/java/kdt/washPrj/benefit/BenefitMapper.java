package kdt.washPrj.benefit;

import kdt.washPrj.benefit.dto.BenefitCampaignDTO;
import kdt.washPrj.benefit.dto.BenefitCampaignRequestDTO;
import kdt.washPrj.benefit.dto.BenefitCampaignStatusDTO;
import kdt.washPrj.benefit.dto.BenefitPolicyDTO;
import kdt.washPrj.benefit.dto.BenefitPolicyRequestDTO;
import kdt.washPrj.benefit.dto.CustomerBenefitOverviewDTO;
import kdt.washPrj.benefit.dto.CustomerBenefitSummaryDTO;
import kdt.washPrj.benefit.dto.CustomerCouponDTO;
import kdt.washPrj.benefit.dto.CustomerVisitRewardProgressDTO;
import kdt.washPrj.benefit.dto.CouponAudienceDTO;
import kdt.washPrj.benefit.dto.PaymentBenefitRefundPolicyDTO;
import kdt.washPrj.benefit.dto.WashBenefitQuoteDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BenefitMapper {

    @Select("""
        SELECT *
        FROM app_api.get_benefit_policy(
            CAST(#{loginId} AS varchar),
            CAST(#{washSiteId} AS bigint)
        )
    """)
    BenefitPolicyDTO selectPolicy(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId
    );

    @Select("""
        SELECT app_api.save_benefit_policy(
            CAST(#{loginId} AS varchar),
            CAST(#{washSiteId} AS bigint),
            CAST(#{request.enabled} AS boolean),
            CAST(#{request.mileageEnabled} AS boolean),
            CAST(#{request.mileageEarnBasisPoints} AS integer),
            CAST(#{request.mileageExpireDays} AS integer),
            CAST(#{request.couponEnabled} AS boolean)
        )
    """)
    String savePolicy(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId,
            @Param("request") BenefitPolicyRequestDTO request
    );

    @Select("""
        SELECT *
        FROM app_api.get_benefit_campaigns(
            CAST(#{loginId} AS varchar),
            CAST(#{washSiteId} AS bigint)
        )
    """)
    List<BenefitCampaignDTO> selectCampaigns(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId
    );

    @Select("""
        SELECT *
        FROM app_api.get_benefit_campaign_statuses(
            CAST(#{loginId} AS varchar),
            CAST(#{washSiteId} AS bigint)
        )
    """)
    List<BenefitCampaignStatusDTO> selectCampaignStatuses(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId
    );

    @Select("""
        SELECT app_api.save_benefit_campaign(
            CAST(#{loginId} AS varchar),
            CAST(#{campaignId} AS bigint),
            CAST(#{request.washSiteId} AS bigint),
            CAST(false AS boolean),
            CAST(#{request.campaignName} AS varchar),
            CAST(#{request.couponType} AS varchar),
            CAST(#{request.issueMode} AS varchar),
            CAST(#{request.discountValue} AS integer),
            CAST(#{request.maxDiscountAmount} AS integer),
            CAST(#{request.minimumPaymentAmount} AS integer),
            CAST(#{request.requiredPaidWashes} AS integer),
            CAST(#{request.couponValidDays} AS integer),
            CAST(#{request.startsAt} AS timestamp),
            CAST(#{request.endsAt} AS timestamp),
            CAST(#{request.active} AS boolean)
        )
    """)
    Long saveCampaign(
            @Param("loginId") String loginId,
            @Param("campaignId") Long campaignId,
            @Param("request") BenefitCampaignRequestDTO request
    );

    @Select("""
        SELECT app_api.issue_benefit_coupon(
            CAST(#{loginId} AS varchar),
            CAST(#{campaignId} AS bigint),
            CAST(#{customerId} AS bigint)
        )
    """)
    Long issueCoupon(
            @Param("loginId") String loginId,
            @Param("campaignId") Long campaignId,
            @Param("customerId") Long customerId
    );

    @Select("""
        SELECT *
        FROM app_api.get_benefit_coupon_audience(
            CAST(#{loginId} AS varchar),
            CAST(#{campaignId} AS bigint),
            CAST(#{audienceType} AS varchar),
            CAST(#{recentDays} AS integer),
            CAST(#{loyalDays} AS integer),
            CAST(#{loyalMinWashes} AS integer),
            CAST(#{dormantDays} AS integer),
            CAST(#{limit} AS integer)
        )
    """)
    List<CouponAudienceDTO> selectCouponAudience(
            @Param("loginId") String loginId,
            @Param("campaignId") Long campaignId,
            @Param("audienceType") String audienceType,
            @Param("recentDays") Integer recentDays,
            @Param("loyalDays") Integer loyalDays,
            @Param("loyalMinWashes") Integer loyalMinWashes,
            @Param("dormantDays") Integer dormantDays,
            @Param("limit") Integer limit
    );

    @Select("SELECT * FROM app_api.get_my_benefit_overview(CAST(#{loginId} AS varchar))")
    List<CustomerBenefitOverviewDTO> selectMyOverview(@Param("loginId") String loginId);

    @Select("SELECT * FROM app_api.get_my_benefit_summary(CAST(#{loginId} AS varchar))")
    CustomerBenefitSummaryDTO selectMySummary(@Param("loginId") String loginId);

    @Select("SELECT * FROM app_api.get_my_visit_reward_progress(CAST(#{loginId} AS varchar))")
    List<CustomerVisitRewardProgressDTO> selectMyVisitRewardProgress(
            @Param("loginId") String loginId
    );

    @Select("""
        SELECT app_api.set_my_benefit_preference(
            CAST(#{loginId} AS varchar),
            CAST(#{washSiteId} AS bigint),
            CAST(#{useAllMileage} AS boolean),
            CAST(#{autoUseCoupon} AS boolean)
        )
    """)
    String saveMyPreference(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId,
            @Param("useAllMileage") Boolean useAllMileage,
            @Param("autoUseCoupon") Boolean autoUseCoupon
    );

    @Select("""
        SELECT *
        FROM app_api.get_my_coupons(
            CAST(#{loginId} AS varchar),
            CAST(#{washSiteId} AS bigint),
            CAST(#{status} AS varchar)
        )
    """)
    List<CustomerCouponDTO> selectMyCoupons(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId,
            @Param("status") String status
    );

    @Select("""
        SELECT app_api.expire_benefit_coupons(
            CAST(#{loginId} AS varchar),
            CAST(#{washSiteId} AS bigint)
        )
    """)
    Integer expireCoupons(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId
    );

    @Select("""
        SELECT *
        FROM app_api.get_admin_coupon_overview(
            CAST(#{loginId} AS varchar),
            CAST(#{ownerId} AS bigint),
            CAST(#{washSiteId} AS bigint),
            CAST(#{status} AS varchar)
        )
    """)
    List<BenefitCampaignStatusDTO> selectAdminCouponOverview(
            @Param("loginId") String loginId,
            @Param("ownerId") Long ownerId,
            @Param("washSiteId") Long washSiteId,
            @Param("status") String status
    );

    @Select("""
        SELECT *
        FROM app_api.select_my_wash_benefit(
            CAST(#{loginId} AS varchar),
            CAST(#{washHistoryId} AS bigint),
            CAST(#{useMileage} AS boolean),
            CAST(#{customerCouponId} AS bigint)
        )
    """)
    WashBenefitQuoteDTO selectWashBenefit(
            @Param("loginId") String loginId,
            @Param("washHistoryId") Long washHistoryId,
            @Param("useMileage") Boolean useMileage,
            @Param("customerCouponId") Long customerCouponId
    );

    @Select("""
        SELECT *
        FROM app_api.get_payment_benefit_refund_policy(
            CAST(#{loginId} AS varchar),
            CAST(#{paymentHistoryId} AS bigint)
        )
    """)
    PaymentBenefitRefundPolicyDTO selectRefundPolicy(
            @Param("loginId") String loginId,
            @Param("paymentHistoryId") Long paymentHistoryId
    );
}
