package kdt.washPrj.benefit.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BenefitCampaignStatusDTO {
    private Long couponCampaignId;
    private Long ownerId;
    private String ownerName;
    private Long washSiteId;
    private String siteName;
    private String campaignName;
    private String couponType;
    private String issueMode;
    private Boolean active;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Long issuedCount;
    private Long availableCount;
    private Long reservedCount;
    private Long usedCount;
    private Long expiredCount;
    private Long canceledCount;
}
