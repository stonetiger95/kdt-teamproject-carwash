package kdt.washPrj.benefit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BenefitPolicyDTO {
    @JsonIgnore
    private String resultCode;
    private Long washSiteId;
    private String siteName;
    private Boolean enabled;
    private Boolean mileageEnabled;
    private Integer mileageEarnBasisPoints;
    private Integer mileageExpireDays;
    private Boolean couponEnabled;
    private LocalDateTime updatedAt;
}
