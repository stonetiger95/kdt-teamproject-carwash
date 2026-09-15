package kdt.washPrj.settlement.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SettlementAbnormalLogDTO {

    private Long washHistoryId;
    private String plateNumber;
    private String customerType;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private String paymentStatus;
    private String washStatus;
    private String reason;
}
