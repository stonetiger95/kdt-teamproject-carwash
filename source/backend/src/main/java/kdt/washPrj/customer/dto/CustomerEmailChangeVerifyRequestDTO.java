package kdt.washPrj.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CustomerEmailChangeVerifyRequestDTO(
        @NotBlank(message = "이메일 변경 요청 정보가 필요합니다.")
        String challengeId,

        @NotBlank(message = "인증번호를 입력해주세요.")
        @Pattern(
                regexp = "^[0-9]{6}$",
                message = "인증번호 6자리를 입력해주세요."
        )
        String code
) {
}
