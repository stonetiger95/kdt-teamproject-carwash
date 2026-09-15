package kdt.washPrj.benefit.dto;

import lombok.Data;

@Data
public class CustomerVisitRewardProgressDTO {
    private Long couponCampaignId;
    private Long ownerId;
    private String ownerName;
    private Long washSiteId;
    private String siteName;
    private String campaignName;
    private Integer paidWashCount;
    private Integer requiredPaidWashes;
    private Integer remainingPaidWashes;
}
