package kdt.washPrj.blackList;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import kdt.washPrj.blackList.dto.AdminVehicleDetailDTO;
import kdt.washPrj.blackList.dto.AdminVehicleListDTO;
import kdt.washPrj.blackList.dto.AdminVehicleSearchDTO;
import kdt.washPrj.blackList.dto.VehicleBlackListDTO;
import kdt.washPrj.blackList.dto.VehicleBlackListRequestDTO;
import kdt.washPrj.blackList.dto.VehicleBlackListSearchDTO;
import kdt.washPrj.global.pagenation.PageResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/admin/vehicle")
public class BlackListController {

    @Resource
    BlackListService service;

    @GetMapping
    public PageResponseDTO<AdminVehicleListDTO> getVehicleList(
            Authentication authentication,
            @Valid AdminVehicleSearchDTO searchDTO
    ) {
        return service.getVehicleList(authentication.getName(), searchDTO);
    }

    @GetMapping("/blacklist")
    public PageResponseDTO<VehicleBlackListDTO> getBlackList(
            Authentication authentication,
            @Valid VehicleBlackListSearchDTO searchDTO
    ) {
        return service.getBlackList(authentication.getName(), searchDTO);
    }

    @GetMapping("/{plateNumber}")
    public ResponseEntity<AdminVehicleDetailDTO> getVehicleDetail(
            Authentication authentication,
            @PathVariable String plateNumber
    ) {
        return ResponseEntity.ok(
                service.getVehicleDetail(authentication.getName(), plateNumber)
        );
    }

    @PostMapping("/{plateNumber}/blacklist")
    public ResponseEntity<String> addManualBlackList(
            Authentication authentication,
            @PathVariable String plateNumber,
            @Valid @RequestBody VehicleBlackListRequestDTO dto
    ) {
        service.addManualBlackList(authentication.getName(), plateNumber, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body("블랙리스트에 등록했습니다.");
    }

    @PatchMapping("/{plateNumber}/blacklist/release")
    public ResponseEntity<String> releaseManualBlackList(
            Authentication authentication,
            @PathVariable String plateNumber
    ) {
        service.releaseManualBlackList(authentication.getName(), plateNumber);
        return ResponseEntity.ok("블랙리스트에서 해제했습니다.");
    }
}
