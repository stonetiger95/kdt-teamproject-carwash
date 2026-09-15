package kdt.washPrj.testConsole.dto;

import lombok.Data;

@Data
public class TestConsoleWashTargetDTO {

    private Long washHistoryId;
    private Long customerId;
    private Integer amount;
    private String paymentStatus;
    private String washStatus;


}
