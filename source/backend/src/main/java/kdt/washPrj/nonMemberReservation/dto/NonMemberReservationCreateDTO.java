package kdt.washPrj.nonMemberReservation.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NonMemberReservationCreateDTO {
    @NotBlank(message = "차량번호를 입력해 주세요.")
    private String plateNumber;
    @NotBlank(message = "연락처를 입력해 주세요.")
    private String phone;
    @NotNull(message = "세차장을 선택해 주세요.")
    @Positive(message = "올바른 세차장을 선택해 주세요.")
    private Long washSiteId;
    // Kept for backward compatibility. Reservation capacity is assigned by DB.
    private Long deviceId;
    @NotNull(message = "세차 코스를 선택해 주세요.")
    @Positive(message = "올바른 세차 코스를 선택해 주세요.")
    private Long courseId;
    @NotNull(message = "예약 시간을 선택해 주세요.")
    @Future(message = "예약 시간은 현재 시각 이후여야 합니다.")
    private LocalDateTime startTime;
}
