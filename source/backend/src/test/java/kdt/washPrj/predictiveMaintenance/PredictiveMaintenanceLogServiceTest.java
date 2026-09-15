package kdt.washPrj.predictiveMaintenance;

import kdt.washPrj.predictiveMaintenance.dto.PredictiveMaintenanceLogPageDTO;
import kdt.washPrj.predictiveMaintenance.dto.PredictiveMaintenanceLogRowDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PredictiveMaintenanceLogServiceTest {
    private PredictiveMaintenanceLogMapper mapper;
    private PredictiveMaintenanceLogService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PredictiveMaintenanceLogMapper.class);
        service = new PredictiveMaintenanceLogService(mapper);
    }

    @Test
    void normalizesFiltersAndReturnsBoundedPage() {
        LocalDateTime fromAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime toAt = fromAt.plusDays(1);
        PredictiveMaintenanceLogRowDTO row = new PredictiveMaintenanceLogRowDTO();
        row.setLogKey("DEMO:ANT-001:20260101000000");
        when(mapper.countLogs("admin", "DEMO", "ANT-001", fromAt, toAt, "WARNING"))
                .thenReturn(125L);
        when(mapper.findLogs("admin", "DEMO", "ANT-001", fromAt, toAt, "WARNING", 100, 100))
                .thenReturn(List.of(row));

        PredictiveMaintenanceLogPageDTO result = service.getLogs(
                "admin", "demo", " ANT-001 ", fromAt, toAt,
                "warning", 1, 500
        );

        assertEquals(1, result.getPage());
        assertEquals(100, result.getSize());
        assertEquals(125, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
        assertEquals(1, result.getContent().size());
        verify(mapper).findLogs(
                "admin", "DEMO", "ANT-001", fromAt, toAt,
                "WARNING", 100, 100
        );
    }

    @Test
    void rejectsInvalidModeAndReversePeriod() {
        assertThrows(ResponseStatusException.class, () -> service.getLogs(
                "admin", "UNKNOWN", null, null, null, null, 0, 50
        ));
        assertThrows(ResponseStatusException.class, () -> service.getLogs(
                "admin", "LIVE", null,
                LocalDateTime.of(2026, 2, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0),
                null, 0, 50
        ));
    }
}

