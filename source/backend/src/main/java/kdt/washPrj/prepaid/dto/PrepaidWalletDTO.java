package kdt.washPrj.prepaid.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PrepaidWalletDTO {

    private Long walletId;
    private Long customerId;
    private Integer balance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
