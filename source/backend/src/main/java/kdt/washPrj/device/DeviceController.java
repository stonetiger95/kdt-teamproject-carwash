package kdt.washPrj.device;

import jakarta.validation.Valid;
import kdt.washPrj.device.dto.DeviceCreateDTO;
import kdt.washPrj.device.dto.DeviceResponseDTO;
import kdt.washPrj.device.dto.DeviceUpdateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/owner/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService service;

    @GetMapping
    public List<DeviceResponseDTO> getList(
            Authentication authentication
    ) {

        return service.getDeviceList(
                authentication.getName()
        );
    }

    @GetMapping("/{deviceId}")
    public DeviceResponseDTO getOne(
            Authentication authentication,
            @PathVariable Long deviceId
    ) {

        return service.getDevice(
                authentication.getName(),
                deviceId
        );
    }

    @PatchMapping("/{deviceId}")
    public void update(
            Authentication authentication,
            @PathVariable Long deviceId,
            @Valid @RequestBody DeviceUpdateDTO dto
    ) {

        service.updateDevice(
                authentication.getName(),
                deviceId,
                dto
        );
    }

    @PostMapping
    public void create(
            Authentication authentication,
            @Valid @RequestBody DeviceCreateDTO dto
    ){
        service.createDevice(
                authentication.getName(),
                dto
        );
    }

    @PatchMapping("/{deviceId}/remove")
    public void remove(
            Authentication authentication,
            @PathVariable Long deviceId
    ) {

        service.removeDevice(
                authentication.getName(),
                deviceId
        );

    }
}
