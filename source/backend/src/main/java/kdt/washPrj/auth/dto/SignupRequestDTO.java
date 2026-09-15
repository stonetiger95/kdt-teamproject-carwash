package kdt.washPrj.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequestDTO {

    @NotBlank(message = "아이디를 입력해주세요.")
    @Size(max = 50, message = "아이디는 50자 이하여야 합니다.")
    @Pattern(
            regexp = "^[A-Za-z0-9]+$",
            message = "아이디는 영문과 숫자만 사용할 수 있습니다."
    )
    private String loginId;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(
            min = 4,
            max = 72,
            message = "비밀번호는 4자 이상 72자 이하여야 합니다."
    )
    private String password;

    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "이메일 형식을 확인해주세요.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String emailAddress;

    @NotBlank(message = "성별을 선택해주세요.")
    @Pattern(
            regexp = "^(M|F|N)$",
            message = "성별을 선택해주세요."
    )
    private String gender;

    @NotBlank(message = "본인인증 요청번호를 입력해주세요.")
    @Pattern(
            regexp = "^[A-Za-z0-9]{16,40}$",
            message = "본인인증 요청번호 형식을 확인해주세요."
    )
    private String identityVerificationId;

    @NotBlank(message = "본인인증 증명을 입력해주세요.")
    @Size(max = 4096, message = "본인인증 증명이 너무 깁니다.")
    private String identityProof;

    @NotBlank(message = "본인인증 상태값을 입력해주세요.")
    @Size(max = 2048, message = "본인인증 상태값이 너무 깁니다.")
    private String identityStateToken;
}
