package kdt.washPrj.auth.recovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kdt.washPrj.auth.recovery.PasswordRecoveryMethod;

public record IdentityRecoveryStartRequestDTO(
        @NotBlank(message = "아이디를 입력해주세요.")
        @Size(max = 50, message = "아이디는 50자 이하여야 합니다.")
        @Pattern(
                regexp = "^[A-Za-z0-9]+$",
                message = "아이디는 영문과 숫자만 사용할 수 있습니다."
        )
        String loginId,

        @NotNull(message = "본인인증 방법을 선택해주세요.")
        PasswordRecoveryMethod method
) {
}
