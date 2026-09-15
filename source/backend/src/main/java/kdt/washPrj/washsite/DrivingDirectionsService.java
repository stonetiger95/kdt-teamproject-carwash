package kdt.washPrj.washsite;

import kdt.washPrj.washsite.dto.DrivingRouteResponseDTO;
import kdt.washPrj.washsite.dto.WashSiteMapDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DrivingDirectionsService {

    private final WashSiteService washSiteService;
    private final NaverDirectionsClient directionsClient;

    public DrivingRouteResponseDTO getRoute(
            double startLatitude,
            double startLongitude,
            Long washSiteId
    ) {
        if (washSiteId == null || washSiteId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "세차장을 확인해주세요."
            );
        }

        WashSiteMapDTO washSite = washSiteService.getMapWashSite(washSiteId);
        if (washSite.getLatitude() == null || washSite.getLongitude() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "선택한 세차장의 위치 정보가 없습니다."
            );
        }

        return directionsClient.getDrivingRoute(
                startLatitude,
                startLongitude,
                washSite.getLatitude(),
                washSite.getLongitude()
        );
    }
}
