package kdt.washPrj.predictiveMaintenance;

import kdt.washPrj.predictiveMaintenance.dto.V2ShadowPredictionResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PredictiveMaintenanceControllerShadowTest {
    private PredictiveMaintenanceService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PredictiveMaintenanceService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PredictiveMaintenanceController(
                        service,
                        mock(PredictiveMaintenanceSimulationService.class),
                        mock(PredictiveMaintenanceNotificationDispatcher.class)
                )
        ).build();
    }

    @Test
    void returnsCandidateResultWithoutChangingProductionContract()
            throws Exception {
        LocalDateTime collectedAt =
                LocalDateTime.of(2026, 4, 2, 10, 0);
        V2ShadowPredictionResultDTO result =
                new V2ShadowPredictionResultDTO();
        result.setEquipmentId("ANT-001");
        result.setStatus("SCORED");
        result.setRiskScore(31.25);
        result.setRiskLevel("WARNING");
        result.setModelVersion("logistic-v2-candidate");
        result.setShadowOnly(true);
        result.setFeatureCount(13);
        result.setHistoryRows(24);

        when(service.predictShadow(
                eq("admin"), eq("ANT-001"), eq(collectedAt), eq("DEMO")
        )).thenReturn(result);

        mockMvc.perform(get(
                        "/api/admin/predictive-maintenance/equipment/{id}/shadow",
                        "ANT-001"
                )
                        .principal(new UsernamePasswordAuthenticationToken(
                                "admin", "", List.of()
                        ))
                        .param("collectedAt", "2026-04-02T10:00:00")
                        .param("mode", "DEMO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCORED"))
                .andExpect(jsonPath("$.risk_score").value(31.25))
                .andExpect(jsonPath("$.model_version")
                        .value("logistic-v2-candidate"))
                .andExpect(jsonPath("$.shadow_only").value(true))
                .andExpect(jsonPath("$.feature_count").value(13))
                .andExpect(jsonPath("$.history_rows").value(24));
    }
}
