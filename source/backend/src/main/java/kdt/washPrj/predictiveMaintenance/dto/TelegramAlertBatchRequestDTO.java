package kdt.washPrj.predictiveMaintenance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TelegramAlertBatchRequestDTO {

    @Valid
    @NotEmpty
    @Size(max = 50)
    private List<TelegramAlertRequestDTO> alerts;
}
