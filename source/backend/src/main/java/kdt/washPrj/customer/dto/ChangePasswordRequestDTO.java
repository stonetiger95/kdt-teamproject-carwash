package kdt.washPrj.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequestDTO {

    @NotBlank(message = "현재 비밀번호를 입력해주세요.")
    @Size(max = 72, message = "현재 비밀번호는 72자 이하여야 합니다.")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    @Size(
            min = 4,
            max = 72,
            message = "새 비밀번호는 4자 이상 72자 이하여야 합니다."
    )
    private String newPassword;
}
