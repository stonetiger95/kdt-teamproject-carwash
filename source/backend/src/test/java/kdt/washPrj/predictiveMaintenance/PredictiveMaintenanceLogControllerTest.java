package kdt.washPrj.predictiveMaintenance;

import kdt.washPrj.predictiveMaintenance.dto.PredictiveMaintenanceLogPageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PredictiveMaintenanceLogControllerTest {
    private PredictiveMaintenanceLogService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PredictiveMaintenanceLogService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PredictiveMaintenanceLogController(service)
        ).build();
    }

    @Test
    void returnsPaginatedSensorLogs() throws Exception {
        when(service.getLogs(
                eq("admin"), eq("DEMO"), eq("ANT-001"),
                eq(null), eq(null), eq("CRITICAL"), eq(0), eq(25)
        )).thenReturn(new PredictiveMaintenanceLogPageDTO(
                List.of(), 0, 25, 0, 0
        ));

        mockMvc.perform(get("/api/admin/predictive-maintenance/logs")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "admin", "", List.of()
                        ))
                        .param("mode", "DEMO")
                        .param("equipmentId", "ANT-001")
                        .param("riskLevel", "CRITICAL")
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isArray());
    }
}

