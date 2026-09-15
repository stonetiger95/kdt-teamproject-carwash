package kdt.washPrj.washsite;

import kdt.washPrj.washsite.dto.DrivingRouteResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/directions")
@RequiredArgsConstructor
public class DrivingDirectionsController {

    private final DrivingDirectionsService directionsService;

    @GetMapping("/driving")
    public DrivingRouteResponseDTO getDrivingRoute(
            @RequestParam double startLatitude,
            @RequestParam double startLongitude,
            @RequestParam Long washSiteId
    ) {
        return directionsService.getRoute(
                startLatitude,
                startLongitude,
                washSiteId
        );
    }
}
