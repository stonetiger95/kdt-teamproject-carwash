package kdt.washPrj.auth.recovery.dto;

import jakarta.validation.constraints.NotNull;
import kdt.washPrj.auth.recovery.PasswordRecoveryMethod;

public record AccountIdIdentityStartRequestDTO(
        @NotNull(message = "본인인증 방법을 선택해주세요.")
        PasswordRecoveryMethod method
) {
}
