package kdt.washPrj.owner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OwnerMyPageDTO {

    private Integer ownerId;

    private String loginId;

    @NotBlank(message = "업주명을 입력해주세요.")
    @Size(max = 50, message = "업주명은 50자 이하여야 합니다.")
    private String ownerName;

    @NotBlank(message = "사업자번호를 입력해주세요.")
    @Pattern(
            regexp = "^(?:[0-9]{10}|[0-9]{3}-[0-9]{2}-[0-9]{5})$",
            message = "사업자번호 형식을 확인해주세요."
    )
    private String businessNumber;

    @NotBlank(message = "전화번호를 입력해주세요.")
    @Pattern(
            regexp = "^(?:01[016789][0-9]{7,8}|01[016789]-[0-9]{3,4}-[0-9]{4})$",
            message = "전화번호 형식을 확인해주세요."
    )
    private String phone;

    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "이메일 형식을 확인해주세요.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String emailAddress;

    private String status;

    @NotBlank(message = "사업장 주소를 입력해주세요.")
    @Size(max = 255, message = "사업장 주소는 255자 이하여야 합니다.")
    private String businessAddress;
}
