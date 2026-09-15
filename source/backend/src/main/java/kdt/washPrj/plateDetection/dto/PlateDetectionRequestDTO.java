package kdt.washPrj.plateDetection.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class PlateDetectionRequestDTO {
    @NotNull(message = "번호판 이미지를 첨부해주세요.")
    private MultipartFile image;

    @NotNull(message = "장치 ID를 입력해주세요.")
    @Positive(message = "올바른 장치 ID를 입력해주세요.")
    private Integer deviceId;
}
