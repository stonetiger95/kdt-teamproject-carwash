package kdt.washPrj.benefit.dto;

import lombok.Data;

@Data
public class CustomerBenefitOverviewDTO {
    private Long ownerId;
    private String ownerName;
    private Long washSiteId;
    private String siteName;
    private Boolean mileageEnabled;
    private Integer mileageEarnBasisPoints;
    private Integer mileageExpireDays;
    private Boolean couponEnabled;
    private Integer mileageBalance;
    private Integer pendingClawback;
    private Boolean useAllMileage;
    private Boolean autoUseCoupon;
    private Long availableCouponCount;
}
