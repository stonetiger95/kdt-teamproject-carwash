package kdt.washPrj.gate;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import kdt.washPrj.gate.dto.GateResponseDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionRequestDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RequestMapping("/api/gates")
@RestController
public class GateController {
    @Resource
    GateService service;

    @PostMapping("/detect")
    public GateResponseDTO detect(@Valid PlateDetectionRequestDTO request){
        return service.handleByDevice(request);
    }

    @PostMapping("/entry")
    public GateResponseDTO entry(@Valid PlateDetectionRequestDTO request){
        return service.enter(request);
    }

    @PostMapping("/exit")
    public GateResponseDTO exit(@Valid PlateDetectionRequestDTO request){
        return service.exit(request);
    }

}
