package kdt.washPrj.auth.recovery.dto;

import lombok.Data;

@Data
public class AccountIdRecoveryResultDTO {
    private String resultCode;
    private String loginId;
}
