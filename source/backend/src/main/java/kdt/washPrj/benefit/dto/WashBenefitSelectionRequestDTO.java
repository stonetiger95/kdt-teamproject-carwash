package kdt.washPrj.benefit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WashBenefitSelectionRequestDTO {
    @NotNull
    private Boolean useMileage;
    private Long customerCouponId;
}
