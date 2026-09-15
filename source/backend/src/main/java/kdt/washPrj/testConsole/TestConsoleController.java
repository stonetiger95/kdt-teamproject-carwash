package kdt.washPrj.testConsole;

import jakarta.validation.Valid;
import kdt.washPrj.testConsole.dto.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/test-console")
public class TestConsoleController {

    private final TestConsoleService testConsoleService;

    public TestConsoleController(TestConsoleService testConsoleService) {
        this.testConsoleService = testConsoleService;
    }

    @GetMapping("/state")
    public TestConsoleStateDTO getState(
            @RequestParam Long customerId,
            @RequestParam(required = false) Long washHistoryId,
            @RequestParam(required = false) Integer requiredAmount
    ) {
        return testConsoleService.getState(
                customerId,
                washHistoryId,
                requiredAmount
        );
    }

    @PostMapping("/charges")
    public TestConsoleActionResponseDTO simulateCharge(
            @Valid @RequestBody TestConsoleChargeRequestDTO requestDTO
    ) {
        return testConsoleService.simulateCharge(requestDTO);
    }

    @PostMapping("/auto-payments")
    public TestConsoleActionResponseDTO retryAutoPayment(
            @Valid @RequestBody TestConsoleAutoPayRequestDTO requestDTO
    ) {
        return testConsoleService.retryAutoPayment(requestDTO);
    }

    @PostMapping("/onsite-payments")
    public TestConsoleActionResponseDTO simulateOnsitePayment(
            @Valid @RequestBody TestConsoleOnsitePaymentRequestDTO requestDTO
    ) {
        return testConsoleService.simulateOnsitePayment(requestDTO);
    }

    @PostMapping("/wash-completions")
    public TestConsoleActionResponseDTO completeWash(
            @Valid @RequestBody TestConsoleWashCompletionRequestDTO requestDTO
    ) {
        return testConsoleService.completeWash(requestDTO);
    }

    @PostMapping("/walk-in-payment-completions")
    public TestConsoleActionResponseDTO completeWalkInPayment(
            @Valid @RequestBody TestConsoleWashCompletionRequestDTO requestDTO
    ) {
        return testConsoleService.completeWalkInPayment(requestDTO);
    }

    @PostMapping("/vehicle-cleanups")
    public TestConsoleActionResponseDTO cleanupAllTestVehicles() {
        return testConsoleService.cleanupAllTestVehicles();
    }
}
