package kdt.washPrj.auth.recovery;

import kdt.washPrj.auth.recovery.dto.PasswordRecoverySessionResponseDTO;

public record IssuedPasswordRecoverySession(
        String rawToken,
        PasswordRecoverySessionResponseDTO response
) {
}
