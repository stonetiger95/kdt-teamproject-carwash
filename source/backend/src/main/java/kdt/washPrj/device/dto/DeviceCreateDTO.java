package kdt.washPrj.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceCreateDTO {

    @NotNull(message = "세차장을 선택해주세요.")
    @Positive(message = "올바른 세차장을 선택해주세요.")
    private Long washSiteId;

    @NotBlank(message = "장치명을 입력해주세요.")
    @Size(max = 100, message = "장치명은 100자 이하여야 합니다.")
    private String deviceName;

    @NotBlank(message = "장치 종류를 선택해주세요.")
    @Pattern(
            regexp = "^(ENTRY|ENTRY_CAMERA|EXIT|EXIT_CAMERA|WASH|WASH_CAMERA|CAMERA)$",
            message = "허용되지 않는 장치 종류입니다."
    )
    private String deviceType;

    @NotBlank(message = "장치 상태를 선택해주세요.")
    @Pattern(
            regexp = "^(ACTIVE|INACTIVE)$",
            message = "허용되지 않는 장치 상태입니다."
    )
    private String status;

}
