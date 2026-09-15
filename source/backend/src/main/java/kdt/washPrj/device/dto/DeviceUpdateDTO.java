package kdt.washPrj.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceUpdateDTO {

    @NotBlank(message = "장치명을 입력해주세요.")
    @Size(max = 100, message = "장치명은 100자 이하여야 합니다.")
    private String deviceName;

    @NotBlank(message = "장치 상태를 선택해주세요.")
    @Pattern(
            regexp = "^(ACTIVE|INACTIVE)$",
            message = "허용되지 않는 장치 상태입니다."
    )
    private String status;
}
