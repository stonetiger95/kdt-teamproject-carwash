package kdt.washPrj.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerEmailChangeStartRequestDTO(
        @NotBlank(message = "새 이메일을 입력해주세요.")
        @Email(message = "이메일 형식을 확인해주세요.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String emailAddress
) {
}
