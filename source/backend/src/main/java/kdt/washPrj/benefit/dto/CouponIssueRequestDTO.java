package kdt.washPrj.benefit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CouponIssueRequestDTO {
    @NotNull
    private Long customerId;
}
