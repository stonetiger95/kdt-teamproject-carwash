package kdt.washPrj.washsite;

import kdt.washPrj.washsite.dto.WashSiteMapDTO;
import kdt.washPrj.washsite.dto.WashSiteRequestDTO;
import kdt.washPrj.washsite.dto.WashSiteResponseDTO;
import kdt.washPrj.washsite.dto.WashSiteSaveResultDTO;
import kdt.washPrj.businessHour.BusinessHourService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WashSiteServiceTest {

    private WashSiteMapper mapper;
    private NaverGeocodingClient geocodingClient;
    private WashSiteService service;

    @BeforeEach
    void setUp() {
        mapper = mock(WashSiteMapper.class);
        geocodingClient = mock(NaverGeocodingClient.class);
        service = new WashSiteService(
                mapper,
                geocodingClient,
                mock(BusinessHourService.class)
        );
    }

    @Test
    void mapListCalculatesDistanceAndSortsNearestFirst() {
        WashSiteMapDTO farSite = mapSite(1L, 37.5665, 126.9780);
        WashSiteMapDTO nearSite = mapSite(2L, 37.5005, 127.0360);
        when(mapper.selectMapWashSites(null, null, null, null, null))
                .thenReturn(List.of(farSite, nearSite));

        List<WashSiteMapDTO> result = service.getMapWashSites(
                null,
                37.5000,
                127.0365,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(2L, result.get(0).getWashSiteId());
        assertTrue(result.get(0).getDistanceKm() < result.get(1).getDistanceKm());
    }

    @Test
    void ownerListUsesAuthenticatedLoginIdForDatabaseScope() {
        WashSiteResponseDTO site = new WashSiteResponseDTO();
        when(mapper.selectOwnerWashSiteList("owner01")).thenReturn(List.of(site));

        List<WashSiteResponseDTO> result = service.getOwnerWashSiteList("owner01");

        assertEquals(List.of(site), result);
        verify(mapper).selectOwnerWashSiteList("owner01");
    }

    @Test
    void adminListUsesAuthenticatedLoginIdForDatabaseAuthorization() {
        WashSiteResponseDTO site = new WashSiteResponseDTO();
        when(mapper.selectAdminWashSiteList("admin01")).thenReturn(List.of(site));

        List<WashSiteResponseDTO> result = service.getAdminWashSiteList("admin01");

        assertEquals(List.of(site), result);
        verify(mapper).selectAdminWashSiteList("admin01");
    }

    @Test
    void customerReservationSiteListUsesDatabaseApiMapper() {
        WashSiteResponseDTO site = new WashSiteResponseDTO();
        when(mapper.selectCustomerWashSiteList()).thenReturn(List.of(site));

        List<WashSiteResponseDTO> result = service.getCustomerWashSiteList();

        assertEquals(List.of(site), result);
        verify(mapper).selectCustomerWashSiteList();
    }

    @Test
    void mapListRejectsIncompleteReferenceCoordinates() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getMapWashSites(
                        null,
                        37.5,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void updateGeocodesAddressWhenCoordinatesAreMissing() {
        WashSiteRequestDTO request = validRequest();
        when(geocodingClient.geocode(request.getAddress()))
                .thenReturn(Optional.of(new NaverGeocodingClient.Coordinates(37.501, 127.039)));
        when(mapper.saveWashSite("owner01", 1L, request))
                .thenReturn(saveResult(1L));

        service.updateOwnerWashSite("owner01", 1L, request);

        assertEquals(37.501, request.getLatitude());
        assertEquals(127.039, request.getLongitude());
        verify(mapper).saveWashSite("owner01", 1L, request);
    }

    @Test
    void mapListFiltersOutsideRadiusAndTrimsKeyword() {
        WashSiteMapDTO nearSite = mapSite(1L, 37.5005, 127.0360);
        WashSiteMapDTO farSite = mapSite(2L, 37.5665, 126.9780);
        when(mapper.selectMapWashSites("강남", null, null, null, null))
                .thenReturn(List.of(nearSite, farSite));

        List<WashSiteMapDTO> result = service.getMapWashSites(
                "  강남  ",
                37.5000,
                127.0365,
                1.0,
                null,
                null,
                null,
                null
        );

        assertEquals(List.of(nearSite), result);
        verify(mapper).selectMapWashSites("강남", null, null, null, null);
    }

    @Test
    void mapListRejectsIncompleteOrReversedBounds() {
        ResponseStatusException incomplete = assertThrows(
                ResponseStatusException.class,
                () -> service.getMapWashSites(
                        null, null, null, null,
                        37.0, 38.0, null, 128.0
                )
        );
        ResponseStatusException reversed = assertThrows(
                ResponseStatusException.class,
                () -> service.getMapWashSites(
                        null, null, null, null,
                        38.0, 37.0, 126.0, 128.0
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, incomplete.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, reversed.getStatusCode());
        verify(mapper, never()).selectMapWashSites(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void mapDetailRejectsUnknownWashSite() {
        when(mapper.selectMapWashSite(999L)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getMapWashSite(999L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void favoriteListUsesAuthenticatedCustomerLoginId() {
        when(mapper.selectCustomerFavoriteWashSiteIds("user01"))
                .thenReturn(List.of(3L, 1L));

        List<Long> result = service.getCustomerFavoriteWashSiteIds("user01");

        assertEquals(List.of(3L, 1L), result);
        verify(mapper).selectCustomerFavoriteWashSiteIds("user01");
    }

    @Test
    void favoriteAddRejectsUnknownWashSite() {
        when(mapper.insertCustomerWashSiteFavorite("user01", 999L)).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.addCustomerWashSiteFavorite("user01", 999L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void favoriteRemoveIsIdempotent() {
        when(mapper.deleteCustomerWashSiteFavorite("user01", 2L)).thenReturn(1);

        int result = service.removeCustomerWashSiteFavorite("user01", 2L);

        assertEquals(1, result);
        verify(mapper).deleteCustomerWashSiteFavorite("user01", 2L);
    }

    @Test
    void updateUsesProvidedCoordinatesWithoutCallingGeocoder() {
        WashSiteRequestDTO request = validRequest();
        request.setLatitude(37.501);
        request.setLongitude(127.039);
        when(mapper.saveWashSite("owner01", 1L, request))
                .thenReturn(saveResult(1L));

        service.updateOwnerWashSite("owner01", 1L, request);

        verify(geocodingClient, never()).geocode(request.getAddress());
        verify(mapper).saveWashSite("owner01", 1L, request);
    }

    @Test
    void updateRejectsCoordinatesOutsideSouthKorea() {
        WashSiteRequestDTO request = validRequest();
        request.setLatitude(40.0);
        request.setLongitude(127.0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateOwnerWashSite("owner01", 1L, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(mapper, never()).saveWashSite("owner01", 1L, request);
    }

    private WashSiteMapDTO mapSite(Long id, double latitude, double longitude) {
        WashSiteMapDTO site = new WashSiteMapDTO();
        site.setWashSiteId(id);
        site.setLatitude(latitude);
        site.setLongitude(longitude);
        return site;
    }

    private WashSiteRequestDTO validRequest() {
        WashSiteRequestDTO request = new WashSiteRequestDTO();
        request.setSiteName("알파 세차장");
        request.setAddress("서울특별시 강남구 테헤란로 101");
        request.setSitePhone("02-123-4567");
        request.setStatus("ACTIVE");
        request.setCourseName("기본 세차");
        request.setWashPrice(7000);
        return request;
    }

    private WashSiteSaveResultDTO saveResult(Long washSiteId) {
        WashSiteSaveResultDTO result = new WashSiteSaveResultDTO();
        result.setWashSiteId(washSiteId);
        result.setAffectedCount(1);
        return result;
    }
}
