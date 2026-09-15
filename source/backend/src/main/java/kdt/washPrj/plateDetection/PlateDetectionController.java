package kdt.washPrj.plateDetection;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import kdt.washPrj.plateDetection.dto.PlateDetectionRequestDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionResponseDTO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plate-detections")
public class PlateDetectionController {

    @Resource
    PlateDetectionService service;

    @PostMapping
    public PlateDetectionResponseDTO detect(
            @Valid @ModelAttribute PlateDetectionRequestDTO request
            ){
        return service.detectAndSave(request);
    }

    @PostMapping("/preview")
    public PlateDetectionResponseDTO preview(
            @Valid @ModelAttribute PlateDetectionRequestDTO request
    ) {
        return service.detectOnly(request);
    }

}
