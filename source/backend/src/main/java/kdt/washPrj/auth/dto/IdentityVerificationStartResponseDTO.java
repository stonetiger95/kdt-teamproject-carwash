package kdt.washPrj.auth.dto;

import java.time.OffsetDateTime;

public record IdentityVerificationStartResponseDTO(
        String identityVerificationId,
        String stateToken,
        String storeId,
        String channelKey,
        boolean testMode,
        OffsetDateTime expiresAt
) {
}
