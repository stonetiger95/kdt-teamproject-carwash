package kdt.washPrj.washsite.dto;

import java.util.List;

public record DrivingRouteResponseDTO(
        int distanceMeters,
        long durationMillis,
        List<DrivingRoutePointDTO> path
) {
}
