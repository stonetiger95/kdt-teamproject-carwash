package kdt.washPrj.device.dto;

import lombok.Data;

@Data
public class DeviceResponseDTO {

    private Long deviceId;
    private Long washSiteId;

    private String deviceName;
    private String deviceType;
    private String status;
}
