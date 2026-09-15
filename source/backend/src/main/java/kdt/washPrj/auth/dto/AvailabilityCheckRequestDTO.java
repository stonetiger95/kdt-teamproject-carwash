package kdt.washPrj.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AvailabilityCheckRequestDTO {

    @NotBlank(message = "확인할 값을 입력해주세요.")
    @Size(max = 100, message = "확인할 값은 100자 이하여야 합니다.")
    private String value;
}
