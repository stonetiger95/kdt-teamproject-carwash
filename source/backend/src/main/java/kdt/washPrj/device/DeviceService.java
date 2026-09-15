package kdt.washPrj.device;

import kdt.washPrj.device.dto.DeviceCreateDTO;
import kdt.washPrj.device.dto.DeviceResponseDTO;
import kdt.washPrj.device.dto.DeviceUpdateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper mapper;

    public List<DeviceResponseDTO> getDeviceList(String loginId) {
        return mapper.findAll(loginId);
    }

    public DeviceResponseDTO getDevice(String loginId, Long deviceId) {
        DeviceResponseDTO device = mapper.findOne(loginId, deviceId);
        if (device == null) {
            throw deviceNotFound();
        }
        return device;
    }

    public void updateDevice(
            String loginId,
            Long deviceId,
            DeviceUpdateDTO dto
    ) {
        if (mapper.update(loginId, deviceId, dto) == 0) {
            throw deviceNotFound();
        }
    }

    public void createDevice(String loginId, DeviceCreateDTO dto) {
        if (dto == null || dto.getWashSiteId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Wash site is required."
            );
        }
        if (mapper.insert(loginId, dto) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "A device can only be registered at your wash site."
            );
        }
    }

    public void removeDevice(String loginId, Long deviceId) {
        if (mapper.remove(loginId, deviceId) == 0) {
            throw deviceNotFound();
        }
    }

    private ResponseStatusException deviceNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Device was not found."
        );
    }
}
