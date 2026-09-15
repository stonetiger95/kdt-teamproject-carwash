package kdt.washPrj.washsite;

import kdt.washPrj.businessHour.BusinessHourService;
import kdt.washPrj.washsite.dto.OwnerResponseDTO;
import kdt.washPrj.washsite.dto.WashSiteMapDTO;
import kdt.washPrj.washsite.dto.WashSiteRequestDTO;
import kdt.washPrj.washsite.dto.WashSiteResponseDTO;
import kdt.washPrj.washsite.dto.WashSiteSaveResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class WashSiteService {

    private static final int MIN_WASH_PRICE = 100;
    private static final int WASH_PRICE_UNIT = 100;
    private static final double MIN_LATITUDE = 33.0;
    private static final double MAX_LATITUDE = 39.5;
    private static final double MIN_LONGITUDE = 124.0;
    private static final double MAX_LONGITUDE = 132.0;
    private static final double MAX_RADIUS_KM = 100.0;
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final WashSiteMapper washSiteMapper;
    private final BusinessHourService businessHourService;
    private final NaverGeocodingClient naverGeocodingClient;

    public WashSiteService(
            WashSiteMapper washSiteMapper,
            NaverGeocodingClient naverGeocodingClient,
            BusinessHourService businessHourService
    ) {
        this.washSiteMapper = washSiteMapper;
        this.naverGeocodingClient = naverGeocodingClient;
        this.businessHourService = businessHourService;
    }

    public List<OwnerResponseDTO> getOwnerList(String loginId) {
        return washSiteMapper.selectOwnerList(loginId);
    }

    public List<WashSiteResponseDTO> getAdminWashSiteList(String loginId) {
        return washSiteMapper.selectAdminWashSiteList(loginId);
    }

    public List<WashSiteMapDTO> getMapWashSites(
            String keyword,
            Double latitude,
            Double longitude,
            Double radiusKm,
            Double south,
            Double north,
            Double west,
            Double east
    ) {
        validateReferenceCoordinates(latitude, longitude);
        validateRadius(radiusKm);
        validateBounds(south, north, west, east);

        String normalizedKeyword = keyword == null || keyword.isBlank()
                ? null
                : keyword.trim();

        List<WashSiteMapDTO> washSites = washSiteMapper.selectMapWashSites(
                normalizedKeyword,
                south,
                north,
                west,
                east
        );

        if (latitude == null) {
            return washSites;
        }

        washSites.forEach(site -> site.setDistanceKm(calculateDistanceKm(
                latitude,
                longitude,
                site.getLatitude(),
                site.getLongitude()
        )));

        return washSites.stream()
                .filter(site -> radiusKm == null
                        || site.getDistanceKm() != null
                        && site.getDistanceKm() <= radiusKm)
                .sorted(Comparator.comparing(
                        WashSiteMapDTO::getDistanceKm,
                        Comparator.nullsLast(Double::compareTo)
                ))
                .toList();
    }

    public WashSiteMapDTO getMapWashSite(Long washSiteId) {
        WashSiteMapDTO washSite = washSiteMapper.selectMapWashSite(washSiteId);
        if (washSite == null) {
            throw new ResponseStatusException(NOT_FOUND, "Wash site not found.");
        }
        return washSite;
    }

    public List<Long> getCustomerFavoriteWashSiteIds(String loginId) {
        return washSiteMapper.selectCustomerFavoriteWashSiteIds(loginId);
    }

    @Transactional
    public int addCustomerWashSiteFavorite(String loginId, Long washSiteId) {
        validateWashSiteId(washSiteId);
        int result = washSiteMapper.insertCustomerWashSiteFavorite(loginId, washSiteId);
        if (result == 0) {
            throw new ResponseStatusException(NOT_FOUND, "Wash site not found.");
        }
        return result;
    }

    @Transactional
    public int removeCustomerWashSiteFavorite(String loginId, Long washSiteId) {
        validateWashSiteId(washSiteId);
        return washSiteMapper.deleteCustomerWashSiteFavorite(loginId, washSiteId);
    }

    public List<WashSiteResponseDTO> getAdminWashSiteListByOwnerId(String loginId, Long ownerId) {
        return washSiteMapper.selectAdminWashSiteListByOwnerId(loginId, ownerId);
    }

    public WashSiteResponseDTO getAdminWashSite(String loginId, Long washSiteId) {
        WashSiteResponseDTO washSite = washSiteMapper.selectAdminWashSite(loginId, washSiteId);
        if (washSite == null) {
            throw new ResponseStatusException(NOT_FOUND, "Wash site not found.");
        }

        return washSite;
    }

    public List<WashSiteResponseDTO> getOwnerWashSiteList(String loginId) {
        return washSiteMapper.selectOwnerWashSiteList(loginId);
    }

    public WashSiteResponseDTO getOwnerWashSite(String loginId, Long washSiteId) {
        WashSiteResponseDTO washSite = washSiteMapper.selectOwnerWashSite(loginId, washSiteId);
        if (washSite == null) {
            throw new ResponseStatusException(FORBIDDEN, "Cannot access this wash site.");
        }

        return washSite;
    }

    @Transactional
    public int createOwnerWashSite(String loginId, WashSiteRequestDTO requestDTO) {
        validateWashSiteRequest(requestDTO);
        prepareCoordinates(requestDTO);
        requestDTO.setOwnerId(null);
        WashSiteSaveResultDTO result = washSiteMapper.saveWashSite(
                loginId,
                null,
                requestDTO
        );

        if (result == null
                || result.getAffectedCount() == null
                || result.getAffectedCount() == 0
                || result.getWashSiteId() == null) {
            throw new ResponseStatusException(FORBIDDEN, "Owner account is required.");
        }
        requestDTO.setWashSiteId(result.getWashSiteId());
        businessHourService.createDefaultBusinessHours(
                loginId,
                requestDTO.getWashSiteId()
        );

        return result.getAffectedCount();
    }

    @Transactional
    public int createAdminWashSite(String loginId, WashSiteRequestDTO requestDTO) {
        validateWashSiteRequest(requestDTO);
        prepareCoordinates(requestDTO);
        WashSiteSaveResultDTO result = washSiteMapper.saveWashSite(
                loginId,
                null,
                requestDTO
        );

        if (result == null
                || result.getAffectedCount() == null
                || result.getAffectedCount() == 0
                || result.getWashSiteId() == null) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Wash site was not created.");
        }
        requestDTO.setWashSiteId(result.getWashSiteId());
        businessHourService.createDefaultBusinessHours(
                loginId,
                requestDTO.getWashSiteId()
        );

        return result.getAffectedCount();
    }

    @Transactional
    public int updateOwnerWashSite(String loginId, Long washSiteId, WashSiteRequestDTO requestDTO) {
        validateWashSiteRequest(requestDTO);
        prepareCoordinates(requestDTO);
        requestDTO.setOwnerId(null);
        WashSiteSaveResultDTO result = washSiteMapper.saveWashSite(
                loginId,
                washSiteId,
                requestDTO
        );
        if (result == null
                || result.getAffectedCount() == null
                || result.getAffectedCount() == 0) {
            throw new ResponseStatusException(FORBIDDEN, "Cannot modify this wash site.");
        }

        return result.getAffectedCount();
    }

    @Transactional
    public int updateAdminWashSite(
            String loginId,
            Long washSiteId,
            WashSiteRequestDTO requestDTO
    ) {
        validateWashSiteRequest(requestDTO);
        prepareCoordinates(requestDTO);
        WashSiteSaveResultDTO result = washSiteMapper.saveWashSite(
                loginId,
                washSiteId,
                requestDTO
        );
        if (result == null
                || result.getAffectedCount() == null
                || result.getAffectedCount() == 0) {
            throw new ResponseStatusException(NOT_FOUND, "Wash site not found.");
        }

        return result.getAffectedCount();
    }

    //고객용 세차장 조회
    public List<WashSiteResponseDTO>
    getCustomerWashSiteList() {
        return washSiteMapper.selectCustomerWashSiteList();
    }

    private void validateWashSiteRequest(WashSiteRequestDTO requestDTO) {
        if (requestDTO == null
                || requestDTO.getSiteName() == null
                || requestDTO.getSiteName().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "siteName is required.");
        }

        if (requestDTO.getAddress() == null || requestDTO.getAddress().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "address is required.");
        }

        if (requestDTO.getWashPrice() == null
                || requestDTO.getWashPrice() < MIN_WASH_PRICE
                || requestDTO.getWashPrice() % WASH_PRICE_UNIT != 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "washPrice must be at least 100 and entered in units of 100."
            );
        }

        if (requestDTO.getSitePhone() == null || requestDTO.getSitePhone().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "sitePhone is required.");
        }

        String sitePhone = requestDTO.getSitePhone().trim();
        long digitCount = sitePhone.chars().filter(Character::isDigit).count();
        if (sitePhone.length() > 20
                || !sitePhone.matches("[0-9()\\-\\s]+")
                || digitCount < 8
                || digitCount > 12) {
            throw new ResponseStatusException(BAD_REQUEST, "sitePhone format is invalid.");
        }

        requestDTO.setSitePhone(sitePhone);
    }

    private void validateWashSiteId(Long washSiteId) {
        if (washSiteId == null || washSiteId <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "washSiteId must be positive.");
        }
    }

    private void prepareCoordinates(WashSiteRequestDTO requestDTO) {
        Double latitude = requestDTO.getLatitude();
        Double longitude = requestDTO.getLongitude();

        if ((latitude == null) != (longitude == null)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "latitude and longitude must be provided together."
            );
        }

        if (latitude != null) {
            validateCoordinateRange(latitude, longitude);
            return;
        }

        naverGeocodingClient.geocode(requestDTO.getAddress())
                .ifPresent(coordinates -> {
                    requestDTO.setLatitude(coordinates.latitude());
                    requestDTO.setLongitude(coordinates.longitude());
                });
    }

    private void validateReferenceCoordinates(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "latitude and longitude must be provided together."
            );
        }

        if (latitude != null) {
            validateCoordinateRange(latitude, longitude);
        }
    }

    private void validateCoordinateRange(double latitude, double longitude) {
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE
                || longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Coordinates must be within South Korea."
            );
        }
    }

    private void validateRadius(Double radiusKm) {
        if (radiusKm != null && (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "radiusKm must be greater than 0 and at most 100."
            );
        }
    }

    private void validateBounds(
            Double south,
            Double north,
            Double west,
            Double east
    ) {
        boolean hasAny = south != null || north != null || west != null || east != null;
        boolean hasAll = south != null && north != null && west != null && east != null;

        if (hasAny && !hasAll) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "south, north, west and east must be provided together."
            );
        }

        if (!hasAll) {
            return;
        }

        validateCoordinateRange(south, west);
        validateCoordinateRange(north, east);
        if (south > north || west > east) {
            throw new ResponseStatusException(BAD_REQUEST, "Map bounds are invalid.");
        }
    }

    private Double calculateDistanceKm(
            double fromLatitude,
            double fromLongitude,
            Double toLatitude,
            Double toLongitude
    ) {
        if (toLatitude == null || toLongitude == null) {
            return null;
        }

        double latitudeDelta = Math.toRadians(toLatitude - fromLatitude);
        double longitudeDelta = Math.toRadians(toLongitude - fromLongitude);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(fromLatitude))
                * Math.cos(Math.toRadians(toLatitude))
                * Math.sin(longitudeDelta / 2)
                * Math.sin(longitudeDelta / 2);
        double distance = EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(distance * 100.0) / 100.0;
    }

}
