package kdt.washPrj.auth.recovery.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class BeginPasswordRecoveryResultDTO {
    private String resultCode;
    private String destinationEmail;
    private OffsetDateTime challengeExpiresAt;
    private OffsetDateTime resendAvailableAt;
}
