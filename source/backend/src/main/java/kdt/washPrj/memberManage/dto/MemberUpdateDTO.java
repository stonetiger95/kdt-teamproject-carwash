package kdt.washPrj.memberManage.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MemberUpdateDTO {
    @Pattern(
            regexp = "^.*\\S.*$",
            message = "이름을 입력해주세요."
    )
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @Past(message = "생년월일은 오늘보다 이전 날짜여야 합니다.")
    private LocalDate birthDate;

    @Pattern(
            regexp = "^(?:01[016789][0-9]{7,8}|01[016789]-[0-9]{3,4}-[0-9]{4})$",
            message = "휴대전화번호 형식을 확인해주세요."
    )
    private String phone;

    @Pattern(
            regexp = "^(MEMBER|NON_MEMBER)$",
            message = "허용되지 않는 고객 구분입니다."
    )
    private String customerType;

    @Pattern(
            regexp = "^(ACTIVE|SUSPENDED)$",
            message = "허용되지 않는 회원 상태입니다."
    )
    private String status;

    @Pattern(
            regexp = "^(M|F|N)$",
            message = "허용되지 않는 성별 값입니다."
    )
    private String gender;

    @Email(message = "이메일 형식을 확인해주세요.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String emailAddress;
}
