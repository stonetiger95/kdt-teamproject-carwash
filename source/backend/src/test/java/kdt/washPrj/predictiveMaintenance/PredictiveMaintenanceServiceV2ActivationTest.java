package kdt.washPrj.predictiveMaintenance;

import kdt.washPrj.predictiveMaintenance.dto.DashboardResponseDTO;
import kdt.washPrj.predictiveMaintenance.dto.EquipmentStatusDTO;
import kdt.washPrj.predictiveMaintenance.dto.PredictionResultDTO;
import kdt.washPrj.predictiveMaintenance.dto.SensorReadingDTO;
import kdt.washPrj.predictiveMaintenance.dto.V2ShadowPredictionResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PredictiveMaintenanceServiceV2ActivationTest {

    private PredictiveMaintenanceMapper mapper;
    private PredictiveMaintenanceClient client;
    private PredictiveMaintenanceService service;
    private LocalDateTime collectedAt;
    private SensorReadingDTO latest;

    @BeforeEach
    void setUp() {
        mapper = mock(PredictiveMaintenanceMapper.class);
        client = mock(PredictiveMaintenanceClient.class);
        PredictiveMaintenanceProperties properties =
                new PredictiveMaintenanceProperties();
        properties.setActiveModel("v2");
        properties.setFallbackToV1(true);
        service = new PredictiveMaintenanceService(
                mapper,
                client,
                mock(TelegramNotificationClient.class),
                properties
        );
        collectedAt = LocalDateTime.of(2026, 4, 2, 10, 0);
        latest = reading(collectedAt);
        when(mapper.findDemoReadingsAt("admin", collectedAt))
                .thenReturn(List.of(latest));
        when(mapper.findDemoHistory(
                "admin", "ANT-001", collectedAt, 168
        )).thenReturn(history());
    }

    @Test
    void usesScoredV2AsPrimaryDashboardDecision() {
        V2ShadowPredictionResultDTO v2 = new V2ShadowPredictionResultDTO();
        v2.setStatus("SCORED");
        v2.setRiskScore(41.2);
        v2.setRiskLevel("WARNING");
        v2.setModelVersion("logistic-v2-active");
        v2.setHistoryRows(24);
        when(client.predictV2(any())).thenReturn(v2);

        DashboardResponseDTO dashboard =
                service.getDemoDashboard("admin", collectedAt);
        EquipmentStatusDTO result = dashboard.getEquipment().get(0);

        assertEquals(41.2, result.getRiskScore());
        assertEquals("WARNING", result.getRiskLevel());
        assertEquals("logistic-v2-active", result.getModelVersion());
        assertEquals("V2_ACTIVE", result.getPredictionSource());
        assertEquals("SCORED", result.getV2Status());
        verify(client, never()).predict(any());
    }

    @Test
    void fallsBackToV1WhileV2HistoryIsWarmingUp() {
        V2ShadowPredictionResultDTO warming = new V2ShadowPredictionResultDTO();
        warming.setStatus("WARMING_UP");
        warming.setModelVersion("logistic-v2-active");
        warming.setHistoryRows(12);
        warming.setMessage("최근 24시간의 연속 센서 로그가 필요합니다.");
        when(client.predictV2(any())).thenReturn(warming);

        PredictionResultDTO v1 = new PredictionResultDTO();
        v1.setRiskScore(5.0);
        v1.setRiskLevel("NORMAL");
        v1.setModelVersion("logistic-platt-v1");
        when(client.predict(any())).thenReturn(v1);

        DashboardResponseDTO dashboard =
                service.getDemoDashboard("admin", collectedAt);
        EquipmentStatusDTO result = dashboard.getEquipment().get(0);

        assertEquals("V1_FALLBACK", result.getPredictionSource());
        assertEquals("WARMING_UP", result.getV2Status());
        assertEquals("logistic-platt-v1", result.getModelVersion());
        assertEquals(
                "최근 24시간의 연속 센서 로그가 필요합니다.",
                result.getFallbackReason()
        );
    }

    private List<SensorReadingDTO> history() {
        List<SensorReadingDTO> rows = new ArrayList<>();
        for (int index = 23; index >= 0; index--) {
            rows.add(reading(collectedAt.minusHours(index)));
        }
        return rows;
    }

    private SensorReadingDTO reading(LocalDateTime time) {
        SensorReadingDTO row = new SensorReadingDTO();
        row.setEquipmentId("ANT-001");
        row.setLocationId("TG-01");
        row.setEquipmentType("car-wash");
        row.setCollectedAt(time);
        row.setTrafficLoad(0.5);
        row.setTemperatureC(25.0);
        row.setVoltageV(12.0);
        row.setSignalStrengthDbm(-65.0);
        row.setSuccessRate(98.0);
        row.setResponseTimeMs(80.0);
        row.setRetryCount(1);
        row.setDisconnectCount(0);
        row.setPacketLossRate(0.2);
        row.setErrorCount(0);
        row.setDaysSinceMaintenance(20);
        return row;
    }
}
