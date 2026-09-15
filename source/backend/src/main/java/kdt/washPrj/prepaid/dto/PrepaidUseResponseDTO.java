package kdt.washPrj.prepaid.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class PrepaidUseResponseDTO {

    @JsonIgnore
    private String resultCode;
    private Long paymentHistoryId;
    private Long useHistoryId;
    private Long walletId;
    private Long customerId;
    private Long washHistoryId;
    private Integer amount;
    private Integer balance;
    private String status;

}
