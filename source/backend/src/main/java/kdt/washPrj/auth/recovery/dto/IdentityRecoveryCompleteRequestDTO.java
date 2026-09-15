package kdt.washPrj.auth.recovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kdt.washPrj.auth.recovery.PasswordRecoveryMethod;

public record IdentityRecoveryCompleteRequestDTO(
        @NotBlank(message = "비밀번호 복구 요청번호를 입력해주세요.")
        String recoveryId,

        @NotNull(message = "본인인증 방법을 선택해주세요.")
        PasswordRecoveryMethod method,

        @NotBlank(message = "본인인증 요청번호를 입력해주세요.")
        @Pattern(
                regexp = "^[A-Za-z0-9]{16,40}$",
                message = "본인인증 요청번호 형식을 확인해주세요."
        )
        String identityVerificationId,

        @NotBlank(message = "본인인증 상태값을 입력해주세요.")
        @Size(max = 2048, message = "본인인증 상태값이 너무 깁니다.")
        String stateToken
) {
}
