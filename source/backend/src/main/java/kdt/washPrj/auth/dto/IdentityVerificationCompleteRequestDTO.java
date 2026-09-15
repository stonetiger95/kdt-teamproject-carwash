package kdt.washPrj.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IdentityVerificationCompleteRequestDTO {

    @NotBlank(message = "본인인증 요청번호를 입력해주세요.")
    @Pattern(
            regexp = "^[A-Za-z0-9]{16,40}$",
            message = "본인인증 요청번호 형식을 확인해주세요."
    )
    private String identityVerificationId;

    @NotBlank(message = "본인인증 상태값을 입력해주세요.")
    @Size(max = 2048, message = "본인인증 상태값이 너무 깁니다.")
    private String stateToken;
}
