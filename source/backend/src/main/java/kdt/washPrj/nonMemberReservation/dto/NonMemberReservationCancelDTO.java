package kdt.washPrj.nonMemberReservation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NonMemberReservationCancelDTO {
    @NotBlank(message = "연락처를 입력해 주세요.")
    private String phone;
    @NotBlank(message = "취소 사유를 입력해 주세요.")
    private String cancelReason;
}
