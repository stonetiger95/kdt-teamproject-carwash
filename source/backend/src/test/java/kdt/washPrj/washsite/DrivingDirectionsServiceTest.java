package kdt.washPrj.washsite;

import kdt.washPrj.washsite.dto.DrivingRouteResponseDTO;
import kdt.washPrj.washsite.dto.WashSiteMapDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DrivingDirectionsServiceTest {

    private WashSiteService washSiteService;
    private NaverDirectionsClient directionsClient;
    private DrivingDirectionsService service;

    @BeforeEach
    void setUp() {
        washSiteService = mock(WashSiteService.class);
        directionsClient = mock(NaverDirectionsClient.class);
        service = new DrivingDirectionsService(washSiteService, directionsClient);
    }

    @Test
    void getRouteUsesStoredWashSiteCoordinates() {
        WashSiteMapDTO washSite = new WashSiteMapDTO();
        washSite.setWashSiteId(7L);
        washSite.setLatitude(37.5100);
        washSite.setLongitude(127.1100);
        DrivingRouteResponseDTO expected = new DrivingRouteResponseDTO(
                1250,
                180000,
                List.of()
        );

        when(washSiteService.getMapWashSite(7L)).thenReturn(washSite);
        when(directionsClient.getDrivingRoute(
                37.5000,
                127.1000,
                37.5100,
                127.1100
        )).thenReturn(expected);

        DrivingRouteResponseDTO result = service.getRoute(
                37.5000,
                127.1000,
                7L
        );

        assertSame(expected, result);
        verify(directionsClient).getDrivingRoute(
                37.5000,
                127.1000,
                37.5100,
                127.1100
        );
    }

    @Test
    void getRouteRejectsWashSiteWithoutCoordinates() {
        WashSiteMapDTO washSite = new WashSiteMapDTO();
        washSite.setWashSiteId(7L);
        when(washSiteService.getMapWashSite(7L)).thenReturn(washSite);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getRoute(37.5000, 127.1000, 7L)
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
    }
}
