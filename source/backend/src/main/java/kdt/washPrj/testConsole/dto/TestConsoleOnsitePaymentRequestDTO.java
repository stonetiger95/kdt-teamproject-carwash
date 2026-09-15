package kdt.washPrj.testConsole.dto;

import lombok.Data;

@Data
public class TestConsoleOnsitePaymentRequestDTO {

    private Long washHistoryId;
    private Integer amount;
    private String paymentType;
    private String result;

}
