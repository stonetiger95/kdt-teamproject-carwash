package kdt.washPrj.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CustomerMyPageDTO {

    private Integer customerId;

    private String loginId;

    @NotBlank(message = "이름을 입력해주세요.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @NotNull(message = "생년월일을 입력해주세요.")
    @Past(message = "생년월일은 오늘보다 이전 날짜여야 합니다.")
    private LocalDate birthDate;

    @NotBlank(message = "휴대전화번호를 입력해주세요.")
    @Pattern(
            regexp = "^(?:01[016789][0-9]{7,8}|01[016789]-[0-9]{3,4}-[0-9]{4})$",
            message = "휴대전화번호 형식을 확인해주세요."
    )
    private String phone;

    private String customerType;

    private String status;

    @NotBlank(message = "성별을 선택해주세요.")
    @Pattern(
            regexp = "^(M|F|N)$",
            message = "허용되지 않는 성별 값입니다."
    )
    private String gender;

    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "이메일 형식을 확인해주세요.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String emailAddress;
}
