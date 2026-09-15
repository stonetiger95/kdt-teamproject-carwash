package kdt.washPrj.settlement.dto;

import lombok.Data;

@Data
public class SettlementSiteComparisonDTO {

    private Long washSiteId;
    private Long ownerId;
    private String ownerName;
    private String siteName;

    private Long totalUseCount;
    private Long uniqueCustomerCount;
    private Long memberUseCount;
    private Long nonMemberUseCount;
    private Long totalUseAmount;
    private Long totalPaidAmount;
    private Long waitingPaymentCount;
}
