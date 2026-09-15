package kdt.washPrj.settlement.dto;

import lombok.Data;

import java.util.List;

@Data
public class SettlementSummaryDTO {

    private String periodType;
    private String startDate;
    private String endDate;

    private Long totalUseCount;
    private Long uniqueCustomerCount;
    private Long memberUseCount;
    private Long nonMemberUseCount;

    private Long totalUseAmount;
    private Long totalPaidAmount;

    private Long waitingPaymentCount;
    private Long successPaymentCount;
    private Long failedPaymentCount;
    private Long canceledPaymentCount;

    private Long abnormalLogCount;
    private Long conversionCount;
    private Double conversionRate;

    private List<SettlementTrendDTO> trends;
    private List<SettlementAbnormalLogDTO> abnormalLogs;
}
