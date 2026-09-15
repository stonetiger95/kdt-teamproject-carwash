package kdt.washPrj.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequestDTO {

    @NotBlank(message = "아이디를 입력해주세요.")
    @Size(max = 50, message = "아이디는 50자 이하여야 합니다.")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(max = 72, message = "비밀번호는 72자 이하여야 합니다.")
    private String password;

}
