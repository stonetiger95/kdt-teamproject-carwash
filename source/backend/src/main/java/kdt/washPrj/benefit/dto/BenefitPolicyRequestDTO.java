package kdt.washPrj.benefit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BenefitPolicyRequestDTO {
    @NotNull
    private Boolean enabled;
    @NotNull
    private Boolean mileageEnabled;
    @NotNull
    @Min(0)
    @Max(5000)
    private Integer mileageEarnBasisPoints;
    @NotNull
    @Min(1)
    @Max(3650)
    private Integer mileageExpireDays;
    @NotNull
    private Boolean couponEnabled;
}
