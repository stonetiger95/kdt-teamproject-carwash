package kdt.washPrj.auth.recovery.dto;

import java.time.OffsetDateTime;

public record PasswordRecoverySessionResponseDTO(
        boolean authorized,
        OffsetDateTime expiresAt,
        String method
) {
    public static PasswordRecoverySessionResponseDTO unauthorized() {
        return new PasswordRecoverySessionResponseDTO(
                false,
                null,
                null
        );
    }
}
