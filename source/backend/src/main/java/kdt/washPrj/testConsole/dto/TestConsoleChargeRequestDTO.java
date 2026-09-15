package kdt.washPrj.testConsole.dto;

import lombok.Data;

@Data
public class TestConsoleChargeRequestDTO {

    private Long customerId;
    private Integer amount;
    private String result;

}
