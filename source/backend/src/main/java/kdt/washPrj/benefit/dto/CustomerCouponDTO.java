package kdt.washPrj.benefit.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerCouponDTO {
    private Long customerCouponId;
    private Long couponCampaignId;
    private String campaignName;
    private String couponType;
    private Integer discountValue;
    private Integer maxDiscountAmount;
    private Integer minimumPaymentAmount;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private Long usedWashHistoryId;
    private Long usedPaymentHistoryId;
    private LocalDateTime usedAt;
    private LocalDateTime restoredAt;
}
