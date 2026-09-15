package kdt.washPrj.auth.recovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailRecoveryVerifyRequestDTO(
        @NotBlank(message = "이메일 인증 요청번호를 입력해주세요.")
        String challengeId,

        @NotBlank(message = "인증번호를 입력해주세요.")
        @Pattern(
                regexp = "^[0-9]{6}$",
                message = "인증번호는 숫자 6자리입니다."
        )
        String code
) {
}
