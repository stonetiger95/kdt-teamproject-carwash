package kdt.washPrj.memberManage;

import jakarta.annotation.Resource;
import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.memberManage.dto.MemberManageDetailDTO;
import kdt.washPrj.memberManage.dto.MemberManageListDTO;
import kdt.washPrj.memberManage.dto.MemberManageSearchDTO;
import kdt.washPrj.memberManage.dto.MemberUpdateDTO;
import kdt.washPrj.vehicle.VehicleVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

@Service
public class MemberManageService {
    @Resource
    MemberManageMapper mapper;
    @Resource
    JsonMapper jsonMapper;

    public PageResponseDTO<MemberManageListDTO> memberList(
            String loginId,
            MemberManageSearchDTO searchDTO
    ) {
        normalizeSearchDTO(searchDTO);
        List<MemberManageListDTO> list = mapper.getMemberList(loginId, searchDTO);
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

    public MemberManageDetailDTO memberDetail(String loginId, Integer customerId) {
        MemberManageDetailDTO detail = mapper.getMemberDetail(loginId, customerId);

        if (detail == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Member not found."
            );
        }

        detail.setVehicles(readVehicles(detail.getVehiclesJson()));
        return detail;
    }

    @Transactional
    public MemberManageDetailDTO updateMember(
            String loginId,
            Integer customerId,
            MemberUpdateDTO request
    ) {
        validateStatus(request.getStatus());

        int updatedCount = mapper.updateMemberDetail(loginId, customerId, request);

        if (updatedCount == -1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Withdrawn customer information cannot be changed."
            );
        }

        if (updatedCount == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Member not found."
            );
        }

        return memberDetail(loginId, customerId);
    }

    private List<VehicleVO> readVehicles(String vehiclesJson) {
        if (vehiclesJson == null || vehiclesJson.isBlank()) {
            return List.of();
        }

        try {
            return Arrays.asList(jsonMapper.readValue(vehiclesJson, VehicleVO[].class));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read member vehicle data.", exception);
        }
    }

    private void validateStatus(String status) {
        if (status == null || status.isBlank()) {
            return;
        }

        if (!"ACTIVE".equals(status) && !"SUSPENDED".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported member status."
            );
        }
    }

    private void normalizeSearchDTO(MemberManageSearchDTO searchDTO) {
        if (searchDTO.getKeyword() == null) {
            searchDTO.setKeyword("");
        }

        if (searchDTO.getStatus() == null) {
            searchDTO.setStatus("");
        }

        if (searchDTO.getCustomerType() == null) {
            searchDTO.setCustomerType("");
        }
    }
}
