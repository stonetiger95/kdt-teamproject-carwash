package kdt.washPrj.washCycle;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import kdt.washPrj.plateDetection.dto.PlateDetectionRequestDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/wash-cycles")
public class WashCycleController {
    @Resource
    WashCycleService service;

    @PostMapping("/detect")
    public WashCycleResponseDTO detect(@Valid PlateDetectionRequestDTO request) {
        return service.processWashCamera(request);
    }
}
