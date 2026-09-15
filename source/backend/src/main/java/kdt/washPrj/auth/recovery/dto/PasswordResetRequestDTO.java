package kdt.washPrj.auth.recovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequestDTO(
        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Size(
                min = 8,
                max = 72,
                message = "새 비밀번호는 8자 이상 72자 이하여야 합니다."
        )
        String newPassword
) {
}
