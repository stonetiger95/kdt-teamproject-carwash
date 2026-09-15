package kdt.washPrj.predictiveMaintenance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TelegramDeliveryResultDTO {
    private int requestedCount;
    private int successCount;
    private int failedCount;
    private int skippedCount;
}
