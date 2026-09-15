package kdt.washPrj.settlement.dto;

import lombok.Data;

@Data
public class SettlementTrendDTO {

    private String periodLabel;
    private Long totalUseCount;
    private Long memberUseCount;
    private Long nonMemberUseCount;
    private Long totalUseAmount;
    private Long totalPaidAmount;
}
