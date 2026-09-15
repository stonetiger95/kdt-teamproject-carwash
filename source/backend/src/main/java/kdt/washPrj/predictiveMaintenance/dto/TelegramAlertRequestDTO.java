package kdt.washPrj.predictiveMaintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TelegramAlertRequestDTO {

    @NotBlank
    @Size(max = 255)
    private String eventKey;

    @NotBlank
    @Size(max = 100)
    private String equipmentId;

    @Size(max = 100)
    private String locationId;

    @Size(max = 100)
    private String equipmentType;

    @NotNull
    private LocalDateTime collectedAt;

    @NotNull
    private Double riskScore;

    @NotBlank
    @Size(max = 20)
    private String alertLevel;

    @Size(max = 100)
    private String alertLabel;

    @Size(max = 500)
    private String issueSummary;
}
