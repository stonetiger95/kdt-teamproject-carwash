package kdt.washPrj.device;

import kdt.washPrj.device.dto.DeviceCreateDTO;
import kdt.washPrj.device.dto.DeviceResponseDTO;
import kdt.washPrj.device.dto.DeviceUpdateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeviceServiceTest {

    private DeviceMapper mapper;
    private DeviceService service;

    @BeforeEach
    void setUp() {
        mapper = mock(DeviceMapper.class);
        service = new DeviceService(mapper);
    }

    @Test
    void getDeviceListReturnsOwnerDevices() {
        List<DeviceResponseDTO> expected =
                List.of(new DeviceResponseDTO());
        when(mapper.findAll("owner01")).thenReturn(expected);

        List<DeviceResponseDTO> actual =
                service.getDeviceList("owner01");

        assertSame(expected, actual);
    }

    @Test
    void getDeviceReturnsMappedDevice() {
        DeviceResponseDTO expected = new DeviceResponseDTO();
        when(mapper.findOne("owner01", 3L)).thenReturn(expected);

        DeviceResponseDTO actual =
                service.getDevice("owner01", 3L);

        assertSame(expected, actual);
    }

    @Test
    void updateDeviceUpdatesOwnedDevice() {
        DeviceUpdateDTO request = new DeviceUpdateDTO();
        request.setDeviceName("Exit camera");
        request.setStatus("ACTIVE");
        when(mapper.update("owner01", 3L, request)).thenReturn(1);

        assertDoesNotThrow(() ->
                service.updateDevice("owner01", 3L, request)
        );

        verify(mapper).update("owner01", 3L, request);
    }

    @Test
    void createDeviceRejectsMissingWashSite() {
        DeviceCreateDTO request = new DeviceCreateDTO();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createDevice("owner01", request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void createDeviceRejectsWashSiteOwnedByAnotherOwner() {
        DeviceCreateDTO request = request(2L);
        when(mapper.insert("owner01", request)).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createDevice("owner01", request)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(mapper).insert("owner01", request);
    }

    @Test
    void createDeviceAllowsOwnedWashSite() {
        DeviceCreateDTO request = request(1L);
        when(mapper.insert("owner01", request)).thenReturn(1);

        assertDoesNotThrow(
                () -> service.createDevice("owner01", request)
        );

        verify(mapper).insert("owner01", request);
    }

    @Test
    void removeDeviceRemovesOwnedDevice() {
        when(mapper.remove("owner01", 3L)).thenReturn(1);

        assertDoesNotThrow(() ->
                service.removeDevice("owner01", 3L)
        );

        verify(mapper).remove("owner01", 3L);
    }

    private DeviceCreateDTO request(Long washSiteId) {
        DeviceCreateDTO request = new DeviceCreateDTO();
        request.setWashSiteId(washSiteId);
        request.setDeviceName("Entry camera");
        request.setDeviceType("ENTRY_CAMERA");
        request.setStatus("ACTIVE");
        return request;
    }
}
