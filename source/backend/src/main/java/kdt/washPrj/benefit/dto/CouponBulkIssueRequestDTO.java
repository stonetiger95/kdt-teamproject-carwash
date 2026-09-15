package kdt.washPrj.benefit.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CouponBulkIssueRequestDTO {
    @NotEmpty
    @Size(max = 500)
    private List<@NotNull @Positive Long> customerIds;
}
