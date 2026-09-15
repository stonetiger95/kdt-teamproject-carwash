package kdt.washPrj.auth.recovery.dto;

public record EmailRecoveryStartResponseDTO(
        String challengeId,
        int resendAfterSeconds,
        String message
) {
}
