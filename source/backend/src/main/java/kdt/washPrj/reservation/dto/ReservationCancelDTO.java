package kdt.washPrj.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReservationCancelDTO {

    @NotBlank(message = "취소 사유를 입력해 주세요.")
    @Size(max = 500, message = "취소 사유는 500자 이하여야 합니다.")
    private String cancelReason;
}
