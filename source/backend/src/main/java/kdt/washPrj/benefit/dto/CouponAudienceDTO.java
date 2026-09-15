package kdt.washPrj.benefit.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CouponAudienceDTO {
    private Long customerId;
    private String customerName;
    private String plateNumbers;
    private Long paidWashCount;
    private LocalDateTime firstPaidAt;
    private LocalDateTime lastPaidAt;
    private Long totalMatches;
}
