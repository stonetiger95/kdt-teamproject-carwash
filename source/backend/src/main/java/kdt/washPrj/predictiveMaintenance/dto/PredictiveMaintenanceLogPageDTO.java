package kdt.washPrj.predictiveMaintenance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Server-paginated response for the predictive-maintenance audit screen. */
@Data
@AllArgsConstructor
public class PredictiveMaintenanceLogPageDTO {
    private List<PredictiveMaintenanceLogRowDTO> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}

