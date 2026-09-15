package kdt.washPrj.benefit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomerBenefitPreferenceRequestDTO {
    @NotNull
    private Boolean useAllMileage;
    @NotNull
    private Boolean autoUseCoupon;
}
