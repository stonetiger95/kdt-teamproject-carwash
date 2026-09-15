package kdt.washPrj.blackList;

import jakarta.annotation.Resource;
import kdt.washPrj.blackList.dto.AdminVehicleDetailDTO;
import kdt.washPrj.blackList.dto.AdminVehicleEntryExitDTO;
import kdt.washPrj.blackList.dto.AdminVehicleListDTO;
import kdt.washPrj.blackList.dto.AdminVehicleSearchDTO;
import kdt.washPrj.blackList.dto.VehicleBlackListDTO;
import kdt.washPrj.blackList.dto.VehicleBlackListRequestDTO;
import kdt.washPrj.blackList.dto.VehicleBlackListSearchDTO;
import kdt.washPrj.global.pagenation.PageResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Arrays;
import tools.jackson.databind.json.JsonMapper;

@Service
public class BlackListService {

    @Resource
    BlackListMapper mapper;
    @Resource
    JsonMapper jsonMapper;

    public PageResponseDTO<AdminVehicleListDTO> getVehicleList(
            String loginId,
            AdminVehicleSearchDTO searchDTO
    ) {
        normalizeSearchDTO(searchDTO);

        List<AdminVehicleListDTO> list = mapper.getVehicleList(loginId, searchDTO);
        int totalCount = list.isEmpty() || list.get(0).getTotalElements() == null
                ? 0
                : Math.toIntExact(list.get(0).getTotalElements());

        return new PageResponseDTO<>(
                list,
                searchDTO.getPage(),
                searchDTO.getSize(),
                totalCount
        );
    }

    public AdminVehicleDetailDTO getVehicleDetail(
            String loginId,
            String plateNumber
    ) {
        plateNumber = normalizePlateNumber(plateNumber);

        AdminVehicleDetailDTO detail = mapper.getVehicleDetail(loginId, plateNumber);
        if (detail == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "차량 조회 이력이 없습니다."
            );
        }

        detail.setEntryExitHistory(readEntryExitHistory(
                detail.getEntryExitHistoryJson()
        ));
        detail.setBlackListHistory(readBlackListHistory(
                detail.getBlacklistHistoryJson()
        ));

        return detail;
    }

    public PageResponseDTO<VehicleBlackListDTO> getBlackList(
            String loginId,
            VehicleBlackListSearchDTO searchDTO
    ) {
        normalizeBlackListSearchDTO(searchDTO);

        List<VehicleBlackListDTO> list = mapper.getBlackList(loginId, searchDTO);
        int totalCount = list.isEmpty() || list.get(0).getTotalElements() == null
                ? 0
                : Math.toIntExact(list.get(0).getTotalElements());

        return new PageResponseDTO<>(
                list,
                searchDTO.getPage(),
                searchDTO.getSize(),
                totalCount
        );
    }

    @Transactional
    public void addManualBlackList(
            String loginId,
            String plateNumber,
            VehicleBlackListRequestDTO dto
    ) {
        plateNumber = normalizePlateNumber(plateNumber);

        if (dto == null || dto.getReason() == null || dto.getReason().isBlank()) {
            throw new IllegalArgumentException("블랙리스트 등록 사유를 입력해 주세요.");
        }

        int result = mapper.addManualBlackList(
                loginId,
                plateNumber,
                dto.getReason().trim()
        );

        if (result == -2) {
            throw new IllegalArgumentException("차량 조회 이력이 없습니다.");
        }
        if (result == -3 || result == 0) {
            throw new IllegalStateException("이미 수동 블랙리스트에 등록된 차량입니다.");
        }
        if (result < 0) {
            throw new IllegalStateException("블랙리스트 등록 정책이 최신 상태가 아닙니다.");
        }
    }

    @Transactional
    public void releaseManualBlackList(
            String loginId,
            String plateNumber
    ) {
        plateNumber = normalizePlateNumber(plateNumber);

        int updatedCount = mapper.releaseManualBlackList(loginId, plateNumber);
        if (updatedCount == 0) {
            throw new IllegalStateException("활성화된 수동 블랙리스트 등록 이력이 없습니다.");
        }
    }

    private void normalizeSearchDTO(AdminVehicleSearchDTO searchDTO) {
        if (searchDTO.getKeyword() == null) {
            searchDTO.setKeyword("");
        }
        if (searchDTO.getCustomerType() == null) {
            searchDTO.setCustomerType("");
        } else {
            searchDTO.setCustomerType(searchDTO.getCustomerType().trim().toUpperCase());
        }
        if (!searchDTO.getCustomerType().isEmpty()
                && !searchDTO.getCustomerType().equals("MEMBER")
                && !searchDTO.getCustomerType().equals("NON_MEMBER")) {
            throw new IllegalArgumentException("Customer type filter is invalid.");
        }
        if (searchDTO.getBlacklistStatus() == null) {
            searchDTO.setBlacklistStatus("");
        } else {
            searchDTO.setBlacklistStatus(searchDTO.getBlacklistStatus().trim().toUpperCase());
        }
        if (!searchDTO.getBlacklistStatus().isEmpty()
                && !searchDTO.getBlacklistStatus().equals("BLACKLISTED")
                && !searchDTO.getBlacklistStatus().equals("NORMAL")) {
            throw new IllegalArgumentException("Blacklist status filter is invalid.");
        }
        if (searchDTO.getPage() < 1) {
            searchDTO.setPage(1);
        }
        if (searchDTO.getSize() < 1) {
            searchDTO.setSize(10);
        }
    }

    private void normalizeBlackListSearchDTO(VehicleBlackListSearchDTO searchDTO) {
        if (searchDTO.getKeyword() == null) {
            searchDTO.setKeyword("");
        }
        if (searchDTO.getBlacklistType() == null) {
            searchDTO.setBlacklistType("");
        }
        if (searchDTO.getPage() < 1) {
            searchDTO.setPage(1);
        }
        if (searchDTO.getSize() < 1) {
            searchDTO.setSize(10);
        }
    }

    private String normalizePlateNumber(String plateNumber) {
        if (plateNumber == null || plateNumber.isBlank()) {
            throw new IllegalArgumentException("차량 번호를 입력해 주세요.");
        }

        return plateNumber.replaceAll("[\\s-]+", "").toUpperCase();
    }

    private List<AdminVehicleEntryExitDTO> readEntryExitHistory(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return Arrays.asList(jsonMapper.readValue(
                    json,
                    AdminVehicleEntryExitDTO[].class
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read vehicle entry/exit history.", exception);
        }
    }

    private List<VehicleBlackListDTO> readBlackListHistory(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return Arrays.asList(jsonMapper.readValue(
                    json,
                    VehicleBlackListDTO[].class
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read vehicle blacklist history.", exception);
        }
    }
}
