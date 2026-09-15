package kdt.washPrj.benefit.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CouponBulkIssueResponseDTO {
    private int requestedCount;
    private int issuedCount;
    private List<Long> couponIds;
}
