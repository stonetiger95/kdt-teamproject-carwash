package kdt.washPrj.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VehicleRequestDTO {
    @NotBlank(message = "차량번호를 입력해주세요.")
    @Size(max = 20, message = "차량번호는 20자 이하여야 합니다.")
    @Pattern(
            regexp = "^[0-9]{2,3}[가-힣][0-9]{4}$",
            message = "차량번호 형식을 확인해주세요. 예: 127가3456"
    )
    private String plateNumber;

    @NotBlank(message = "차량 종류를 선택해주세요.")
    @Pattern(
            regexp = "^(SEDAN|SUV|TRUCK|HATCHBACK|VAN)$",
            message = "허용되지 않는 차량 종류입니다."
    )
    private String vehicleType;
}
