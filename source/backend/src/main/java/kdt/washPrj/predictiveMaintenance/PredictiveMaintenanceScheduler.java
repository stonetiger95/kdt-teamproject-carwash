package kdt.washPrj.predictiveMaintenance;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PredictiveMaintenanceScheduler {

    private final PredictiveMaintenanceSimulationService simulationService;

    @Scheduled(
            fixedDelayString = "${predictive-maintenance.simulation.interval-ms:500}"
    )
    public void advanceSimulation() {
        simulationService.advanceAll();
    }
}
