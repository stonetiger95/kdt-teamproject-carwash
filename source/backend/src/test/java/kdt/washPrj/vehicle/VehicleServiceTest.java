package kdt.washPrj.vehicle;

import kdt.washPrj.blackList.BlackListMapper;
import kdt.washPrj.blackList.VehicleBlackListVO;
import kdt.washPrj.customer.CustomerMapper;
import kdt.washPrj.vehicle.dto.VehicleRequestDTO;
import kdt.washPrj.vehicle.dto.VehicleResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleServiceTest {
    private VehicleMapper mapper;
    private CustomerMapper customerMapper;
    private BlackListMapper blackListMapper;
    private VehicleService service;

    @BeforeEach
    void setUp() {
        mapper = mock(VehicleMapper.class);
        customerMapper = mock(CustomerMapper.class);
        blackListMapper = mock(BlackListMapper.class);
        service = new VehicleService(mapper, customerMapper, blackListMapper);
    }

    @Test
    void registerVehicleMapsRequestAndCustomerToVehicle() {
        VehicleRequestDTO request = new VehicleRequestDTO();
        request.setPlateNumber("12가3456");
        request.setVehicleType("SEDAN");
        when(customerMapper.findStatusForUpdate(20)).thenReturn("ACTIVE");
        when(mapper.registerVehicleOld(any())).thenReturn(1);

        assertEquals(1, service.registerVehicleOld(request, 20));

        ArgumentCaptor<VehicleVO> captor = ArgumentCaptor.forClass(VehicleVO.class);
        verify(mapper).registerVehicleOld(captor.capture());
        assertEquals(20, captor.getValue().getCustomerId());
        assertEquals("12가3456", captor.getValue().getPlateNumber());
        assertEquals("SEDAN", captor.getValue().getVehicleType());
        assertNotNull(captor.getValue().getCreatedAt());
    }

    @Test
    void vehicleByCustomerReturnsMapperVehicles() {
        List<VehicleResponseDTO> expected = List.of(new VehicleResponseDTO());
        when(mapper.findByCustomerIdOld(20)).thenReturn(expected);

        assertSame(expected, service.vehicleByCustomerOld(20));
    }

    @Test
    void vehicleDeactivateUsesVehicleAndCustomerIds() {
        VehicleVO vehicle = new VehicleVO();
        vehicle.setPlateNumber("12가3456");
        when(mapper.findActiveByIdAndCustomerOld(10, 20)).thenReturn(vehicle);
        when(mapper.deactivateByVehicleIdOld(10, 20)).thenReturn(1);

        assertEquals(1, service.vehicleDeactivateOld(10, 20));
        verify(mapper).deactivateByVehicleIdOld(10, 20);
        verify(blackListMapper).releaseSuspendedCustomerVehicle(
                20,
                "12가3456"
        );
    }

    @Test
    void vehicleDeactivateRejectsRestrictedVehicle() {
        VehicleVO vehicle = new VehicleVO();
        vehicle.setPlateNumber("12가3456");
        when(mapper.findActiveByIdAndCustomerOld(10, 20)).thenReturn(vehicle);
        when(blackListMapper.findBlacklistsByPlateNumber("12가3456"))
                .thenReturn(new VehicleBlackListVO());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.vehicleDeactivateOld(10, 20)
        );

        assertEquals(HttpStatus.LOCKED, exception.getStatusCode());
        verify(mapper, never()).deactivateByVehicleIdOld(anyInt(), anyInt());
        verify(blackListMapper, never()).releaseSuspendedCustomerVehicle(
                anyInt(),
                anyString()
        );
    }

    @Test
    void registerVehicleRejectsSuspendedCustomer() {
        when(customerMapper.findStatusForUpdate(20)).thenReturn("SUSPENDED");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.registerVehicleOld(request("12가3456"), 20)
        );

        assertEquals(HttpStatus.LOCKED, exception.getStatusCode());
        verify(mapper, never()).registerVehicleOld(any());
    }

    @Test
    void registerVehicleRejectsReactivationWhenThreeVehiclesAreAlreadyActive() {
        when(customerMapper.findStatusForUpdate(20))
                .thenReturn("ACTIVE");
        when(mapper.findByPlateNumber("34나5678"))
                .thenReturn(null); // 해당 차량은 비활성이라 검색되지 않음
        when(mapper.countActiveByCustomerOld(20))
                .thenReturn(3);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.registerVehicleOld(
                        request("34나5678"),
                        20
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(mapper, never()).registerVehicleOld(any());
    }

    @Test
    void registerVehicleRejectsPlateOwnedByAnyActiveCustomer() {
        when(customerMapper.findStatusForUpdate(20)).thenReturn("ACTIVE");
        when(mapper.findByPlateNumber("12가3456"))
                .thenReturn(new VehicleVO());

        DuplicateVehiclePlateException exception = assertThrows(
                DuplicateVehiclePlateException.class,
                () -> service.registerVehicleOld(request("12가 3456"), 20)
        );

        assertEquals(
                "이미 등록된 차량 번호입니다.",
                exception.getMessage()
        );
        verify(mapper, never()).registerVehicleOld(any());
    }

    @Test
    void registerVehicleNormalizesPlateBeforeSaving() {
        when(customerMapper.findStatusForUpdate(20)).thenReturn("ACTIVE");
        when(mapper.registerVehicleOld(any())).thenReturn(1);

        assertEquals(
                1,
                service.registerVehicleOld(request(" ab-12가 3456 "), 20)
        );

        verify(mapper).findByPlateNumber("AB12가3456");
        verify(mapper).registerVehicleOld(argThat(
                vehicle -> "AB12가3456".equals(vehicle.getPlateNumber())
        ));
    }

    @Test
    void registerVehicleMapsOnlyPlateUniqueViolationToDomainException() {
        when(customerMapper.findStatusForUpdate(20)).thenReturn("ACTIVE");
        SQLException sqlException = new SQLException(
                "duplicate key violates unique constraint "
                        + "\"uq_vehicle_active_normalized_plate\"",
                "23505"
        );
        when(mapper.registerVehicleOld(any())).thenThrow(
                new DataIntegrityViolationException(
                        "vehicle insert failed",
                        sqlException
                )
        );

        DuplicateVehiclePlateException exception = assertThrows(
                DuplicateVehiclePlateException.class,
                () -> service.registerVehicleOld(request("12가3456"), 20)
        );

        assertEquals("이미 등록된 차량 번호입니다.", exception.getMessage());
    }

    @Test
    void registerVehicleRethrowsOtherIntegrityViolations() {
        when(customerMapper.findStatusForUpdate(20)).thenReturn("ACTIVE");
        DataIntegrityViolationException original =
                new DataIntegrityViolationException(
                        "foreign key violation",
                        new SQLException(
                                "violates foreign key constraint customer_fk",
                                "23503"
                        )
                );
        when(mapper.registerVehicleOld(any())).thenThrow(original);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.registerVehicleOld(request("12가3456"), 20)
        );

        assertSame(original, thrown);
    }

    private VehicleRequestDTO request(String plateNumber) {
        VehicleRequestDTO request = new VehicleRequestDTO();
        request.setPlateNumber(plateNumber);
        request.setVehicleType("SEDAN");
        return request;
    }
}
