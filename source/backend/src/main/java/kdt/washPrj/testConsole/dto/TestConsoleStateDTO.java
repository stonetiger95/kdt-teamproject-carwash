package kdt.washPrj.testConsole.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TestConsoleStateDTO {

    private Long customerId;
    private String customerName;
    private Long walletId;
    private Integer balance;
    private Integer requiredAmount;
    private Boolean enoughBalance;
    private Boolean billingCardRegistered;
    private Boolean autoPayEnabled;
    private Boolean billingCardTableReady;
    private Boolean prepaidSchemaReady;
    private Long washHistoryId;
    private Integer washAmount;
    private String washPaymentStatus;
    private String washStatus;
    private Long latestPaymentHistoryId;
    private String latestPaymentType;
    private String latestPaymentStatus;
    private Integer latestPaymentAmount;
    private LocalDateTime latestPaidAt;
    private LocalDateTime walletUpdatedAt;

}
