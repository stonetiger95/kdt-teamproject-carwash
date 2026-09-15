package kdt.washPrj.benefit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BenefitCampaignRequestDTO {
    @NotNull
    private Long washSiteId;
    @NotBlank
    @Size(max = 100)
    private String campaignName;
    @NotBlank
    private String couponType;
    @NotBlank
    private String issueMode;
    @Min(0)
    private Integer discountValue = 0;
    @Min(1)
    private Integer maxDiscountAmount;
    @Min(0)
    private Integer minimumPaymentAmount = 0;
    @Min(1)
    @Max(100)
    private Integer requiredPaidWashes;
    @NotNull
    @Min(1)
    @Max(3650)
    private Integer couponValidDays = 30;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    @NotNull
    private Boolean active = true;
}
