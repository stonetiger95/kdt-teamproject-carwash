package kdt.washPrj.auth.recovery.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailRecoveryStartRequestDTO(
        @NotBlank(message = "아이디를 입력해주세요.")
        @Size(max = 50, message = "아이디는 50자 이하여야 합니다.")
        @Pattern(
                regexp = "^[A-Za-z0-9]+$",
                message = "아이디는 영문과 숫자만 사용할 수 있습니다."
        )
        String loginId,

        @NotBlank(message = "가입할 때 등록한 이메일을 입력해주세요.")
        @Email(message = "이메일 형식을 확인해주세요.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String emailAddress
) {
}
