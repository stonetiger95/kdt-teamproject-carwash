package kdt.washPrj.nonMemberReservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class NonMemberReservationAccessDTO {
    @NotNull(message = "예약 ID가 필요합니다.")
    @Positive(message = "올바른 예약 ID를 입력해 주세요.")
    private Long reservationId;
    @NotBlank(message = "연락처를 입력해 주세요.")
    private String phone;
}
