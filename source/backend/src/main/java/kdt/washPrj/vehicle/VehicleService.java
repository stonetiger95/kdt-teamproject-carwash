package kdt.washPrj.vehicle;

import kdt.washPrj.blackList.BlackListMapper;
import kdt.washPrj.customer.CustomerMapper;
import kdt.washPrj.global.vehicle.PlateNumberNormalizer;
import kdt.washPrj.vehicle.dto.VehicleRequestDTO;
import kdt.washPrj.vehicle.dto.VehicleResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleService {
    private static final int MAX_ACTIVE_VEHICLES = 3;

    private static final String UNIQUE_VEHICLE_PLATE_INDEX =
            "uq_vehicle_active_normalized_plate";

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final VehicleMapper mapper;
    private final CustomerMapper customerMapper;
    private final BlackListMapper blackListMapper;

    public int registerVehicle(VehicleRequestDTO dto, String loginId) {
        return mapper.registerVehicle(
                loginId,
                dto.getPlateNumber(),
                dto.getVehicleType()
        );
    }

    @Transactional
    public int registerVehicleOld(VehicleRequestDTO dto, Integer customerId) {
        String customerStatus = customerMapper.findStatusForUpdate(customerId);
        if (!"ACTIVE".equals(customerStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.LOCKED,
                    "활성 회원만 차량을 등록할 수 있습니다."
            );
        }

        String plateNumber = normalizePlateNumber(dto.getPlateNumber());
        if (mapper.findByPlateNumber(plateNumber) != null) {
            throw new DuplicateVehiclePlateException();
        }
        int activeVehicleCount = mapper.countActiveByCustomerOld(customerId);

        if (activeVehicleCount >= MAX_ACTIVE_VEHICLES) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "차량은 최대 3대까지 등록할 수 있습니다."
            );
        }

        VehicleVO vo = new VehicleVO();
        vo.setCustomerId(customerId);
        vo.setPlateNumber(plateNumber);
        vo.setVehicleType(dto.getVehicleType());
        vo.setCreatedAt(LocalDateTime.now());
        try {
            return mapper.registerVehicleOld(vo);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateVehiclePlate(exception)) {
                throw new DuplicateVehiclePlateException();
            }
            throw exception;
        }
    }

    public List<VehicleResponseDTO> vehicleByCustomerOld(Integer customerId) {
        return mapper.findByCustomerIdOld(customerId);
    }

    @Transactional
    public int vehicleDeactivateOld(Integer vehicleId, Integer customerId) {
        VehicleVO vehicle = mapper.findActiveByIdAndCustomerOld(
                vehicleId,
                customerId
        );
        if (vehicle == null) {
            return 0;
        }

        String plateNumber = normalizePlateNumber(vehicle.getPlateNumber());
        if (blackListMapper.findBlacklistsByPlateNumber(plateNumber) != null) {
            throw new ResponseStatusException(
                    HttpStatus.LOCKED,
                    "이용 제한 차량은 삭제할 수 없습니다. 제한 해제 후 다시 시도해 주세요."
            );
        }

        int updatedCount = mapper.deactivateByVehicleIdOld(
                vehicleId,
                customerId
        );
        if (updatedCount > 0) {
            blackListMapper.releaseSuspendedCustomerVehicle(
                    customerId,
                    plateNumber
            );
        }
        return updatedCount;
    }

    public List<VehicleResponseDTO> vehicleByCustomer(String loginId) {
        return mapper.findMyVehicles(loginId);
    }

    public int vehicleDeactivate(Integer vehicleId, String loginId) {
        return mapper.deactivateMyVehicle(loginId, vehicleId);
    }

    private String normalizePlateNumber(String plateNumber) {
        String normalized = PlateNumberNormalizer.normalize(plateNumber);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("차량 번호를 입력해주세요.");
        }
        return normalized;
    }

    private boolean isDuplicateVehiclePlate(
            DataIntegrityViolationException exception
    ) {
        boolean uniqueViolation = false;
        boolean vehiclePlateConstraint = false;
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                uniqueViolation = true;
            }

            String message = cause.getMessage();
            if (message != null && message.contains(UNIQUE_VEHICLE_PLATE_INDEX)) {
                vehiclePlateConstraint = true;
            }

            cause = cause.getCause();
        }

        return uniqueViolation && vehiclePlateConstraint;
    }
}
