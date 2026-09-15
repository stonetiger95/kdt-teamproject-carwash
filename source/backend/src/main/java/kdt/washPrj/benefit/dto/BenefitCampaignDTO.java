package kdt.washPrj.benefit.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BenefitCampaignDTO {
    private Long couponCampaignId;
    private Long ownerId;
    private Long washSiteId;
    private String campaignName;
    private String couponType;
    private String issueMode;
    private Integer discountValue;
    private Integer maxDiscountAmount;
    private Integer minimumPaymentAmount;
    private Integer requiredPaidWashes;
    private Integer couponValidDays;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Boolean active;
}
