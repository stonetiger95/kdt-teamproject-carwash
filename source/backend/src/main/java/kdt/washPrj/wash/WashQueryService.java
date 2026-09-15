package kdt.washPrj.wash;

import kdt.washPrj.global.vehicle.PlateNumberNormalizer;
import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.wash.dto.WashHistorySearchDTO;
import kdt.washPrj.wash.dto.WashResponseDTO;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

@Service
public class WashQueryService {

    private static final Path PLATE_UPLOAD_ROOT = Paths.get("upload/plates")
            .toAbsolutePath()
            .normalize();

    private final WashQueryMapper washQueryMapper;

    public WashQueryService(WashQueryMapper washQueryMapper) {
        this.washQueryMapper = washQueryMapper;
    }

    public WashResponseDTO getWashHistory(String loginId, Long washHistoryId) {
        return washQueryMapper.selectAuthorizedById(loginId, washHistoryId);
    }

    public List<WashResponseDTO> getWashHistoryList(String loginId) {
        WashHistorySearchDTO search = normalizeSearch(new WashHistorySearchDTO());
        return washQueryMapper.selectAuthorizedList(loginId, search, null, 0);
    }

    public PageResponseDTO<WashResponseDTO> getWashHistoryPage(
            String loginId,
            WashHistorySearchDTO search
    ) {
        search = normalizeSearch(search);
        List<WashResponseDTO> content = washQueryMapper.selectAuthorizedPage(
                loginId,
                search
        );
        int totalElements = getTotalElements(content);
        return toPageResponse(content, search, totalElements);
    }

    public List<WashResponseDTO> getCustomerWashHistoryList(
            String loginId,
            WashHistorySearchDTO search
    ) {
        search = normalizeSearch(search);
        return washQueryMapper.selectAuthorizedList(loginId, search, null, 0);
    }

    public WashResponseDTO getCustomerWashHistory(
            String loginId,
            Long washHistoryId
    ) {
        return washQueryMapper.selectAuthorizedById(loginId, washHistoryId);
    }

    public List<WashResponseDTO> getOwnerWashHistoryList(String loginId) {
        WashHistorySearchDTO search = normalizeSearch(new WashHistorySearchDTO());
        return washQueryMapper.selectAuthorizedList(loginId, search, null, 0);
    }

    public PageResponseDTO<WashResponseDTO> getOwnerWashHistoryPage(
            String loginId,
            WashHistorySearchDTO search
    ) {
        search = normalizeSearch(search);
        List<WashResponseDTO> content = washQueryMapper.selectAuthorizedPage(
                loginId,
                search
        );
        int totalElements = getTotalElements(content);
        return toPageResponse(content, search, totalElements);
    }

    private int getTotalElements(List<WashResponseDTO> content) {
        if (content.isEmpty() || content.get(0).getTotalElements() == null) {
            return 0;
        }
        return Math.toIntExact(content.get(0).getTotalElements());
    }

    public WashResponseDTO getOwnerWashHistory(
            String loginId,
            Long washHistoryId
    ) {
        return washQueryMapper.selectAuthorizedById(loginId, washHistoryId);
    }

    private PageResponseDTO<WashResponseDTO> toPageResponse(
            List<WashResponseDTO> content,
            WashHistorySearchDTO search,
            int totalElements
    ) {
        return new PageResponseDTO<>(
                content,
                search.getPage(),
                search.getSize(),
                totalElements
        );
    }

    private WashHistorySearchDTO normalizeSearch(WashHistorySearchDTO search) {
        if (search == null) {
            search = new WashHistorySearchDTO();
        }

        if (search.getStartDate() != null
                && search.getEndDate() != null
                && search.getStartDate().isAfter(search.getEndDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "종료일은 시작일보다 빠를 수 없습니다."
            );
        }

        search.setPlateNumber(normalizeText(search.getPlateNumber()));
        search.setCustomerType(normalizeText(search.getCustomerType()));
        search.setWashStatus(normalizeText(search.getWashStatus()));
        search.setPaymentStatus(normalizeText(search.getPaymentStatus()));
        return search;
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public ResponseEntity<Resource> getCustomerWashHistoryImage(
            String loginId,
            Long washHistoryId,
            String imageType
    ) {
        return getAuthorizedWashHistoryImage(
                washQueryMapper.selectAuthorizedById(loginId, washHistoryId),
                washHistoryId,
                imageType
        );
    }

    public ResponseEntity<Resource> getOwnerWashHistoryImage(
            String loginId,
            Long washHistoryId,
            String imageType
    ) {
        return getAuthorizedWashHistoryImage(
                washQueryMapper.selectAuthorizedById(loginId, washHistoryId),
                washHistoryId,
                imageType
        );
    }

    public ResponseEntity<Resource> getAdminWashHistoryImage(
            String loginId,
            Long washHistoryId,
            String imageType
    ) {
        return getAuthorizedWashHistoryImage(
                washQueryMapper.selectAuthorizedById(loginId, washHistoryId),
                washHistoryId,
                imageType
        );
    }

    public List<WashResponseDTO> getNonMemberPendingWashHistory(
            String plateLast4
    ) {
        String normalizedPlateLast4 = plateLast4 == null
                ? ""
                : plateLast4.trim();

        if (!normalizedPlateLast4.matches("^\\d{4}$")) {
            throw new IllegalArgumentException(
                    "차량번호 뒤 4자리를 입력하세요."
            );
        }

        return washQueryMapper.selectNonMemberPending(
                normalizedPlateLast4
        );
    }

    public List<WashResponseDTO> getNonMemberWashHistoryList(
            String plateNumber
    ) {
        String normalizedPlateNumber = PlateNumberNormalizer.normalize(plateNumber);
        if (normalizedPlateNumber == null) {
            normalizedPlateNumber = "";
        }

        if (!normalizedPlateNumber.matches("^[0-9가-힣]{3,8}\\d{4}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 전체 차량번호를 입력하세요."
            );
        }

        return washQueryMapper.selectNonMemberHistory(
                normalizedPlateNumber
        );
    }

    private ResponseEntity<Resource> getAuthorizedWashHistoryImage(
            WashResponseDTO washHistory,
            Long washHistoryId,
            String imageType
    ) {
        requireWashHistory(washHistory);
        return getWashHistoryImage(washHistoryId, imageType);
    }

    private ResponseEntity<Resource> getWashHistoryImage(
            Long washHistoryId,
            String imageType
    ) {
        String normalizedImageType = normalizeImageType(imageType);
        String filePath = washQueryMapper.selectImagePath(
                washHistoryId,
                normalizedImageType
        );

        if (filePath == null || filePath.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        Path imagePath = Paths.get(filePath).toAbsolutePath().normalize();

        if (!imagePath.startsWith(PLATE_UPLOAD_ROOT)
                || !Files.isRegularFile(imagePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = new UrlResource(imagePath.toUri());
            String detectedContentType = Files.probeContentType(imagePath);
            MediaType contentType = detectedContentType == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(detectedContentType);

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(contentType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename*=UTF-8''" + java.net.URLEncoder.encode(
                                    imagePath.getFileName().toString(),
                                    StandardCharsets.UTF_8
                            )
                    )
                    .body(resource);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "촬영 사진을 불러오지 못했습니다.",
                    e
            );
        }
    }

    private String normalizeImageType(String imageType) {
        String normalized = imageType == null
                ? ""
                : imageType.trim().toUpperCase(Locale.ROOT);

        if (!"ENTRY".equals(normalized) && !"EXIT".equals(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "imageType은 entry 또는 exit만 사용할 수 있습니다."
            );
        }

        return normalized;
    }

    private WashResponseDTO requireWashHistory(WashResponseDTO washHistory) {
        if (washHistory == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "세차 내역을 찾을 수 없습니다."
            );
        }

        return washHistory;
    }
}
