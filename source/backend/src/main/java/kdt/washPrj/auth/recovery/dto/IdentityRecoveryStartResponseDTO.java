package kdt.washPrj.auth.recovery.dto;

import java.time.OffsetDateTime;

public record IdentityRecoveryStartResponseDTO(
        String recoveryId,
        String identityVerificationId,
        String stateToken,
        String storeId,
        String channelKey,
        boolean testMode,
        OffsetDateTime expiresAt
) {
}
