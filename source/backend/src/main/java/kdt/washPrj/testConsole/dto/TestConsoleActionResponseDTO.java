package kdt.washPrj.testConsole.dto;

import lombok.Data;

@Data
public class TestConsoleActionResponseDTO {

    private String action;
    private String result;
    private String message;
    private Long customerId;
    private Long washHistoryId;
    private Long paymentHistoryId;
    private Integer amount;
    private Integer balance;
    private TestConsoleStateDTO state;

}
