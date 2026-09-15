package kdt.washPrj.gate;

import kdt.washPrj.blackList.BlackListMapper;
import kdt.washPrj.blackList.VehicleBlackListVO;
import kdt.washPrj.customer.CustomerMapper;
import kdt.washPrj.gate.dto.GateResponseDTO;
import kdt.washPrj.plateDetection.PlateDetectionService;
import kdt.washPrj.plateDetection.dto.PlateDetectionRequestDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionResponseDTO;
import kdt.washPrj.reservation.ReservationArrivalStatus;
import kdt.washPrj.reservation.ReservationService;
import kdt.washPrj.reservation.dto.ReservationArrivalCheckDTO;
import kdt.washPrj.vehicle.VehicleMapper;
import kdt.washPrj.vehicle.VehicleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

class GateServiceTest {
    private GateMapper mapper;
    private PlateDetectionService plateService;
    private VehicleMapper vehicleMapper;
    private CustomerMapper customerMapper;
    private BlackListMapper blackListMapper;
    private ReservationService reservationService;
    private GateService service;

    @BeforeEach
    void setUp() {
        mapper = mock(GateMapper.class);
        plateService = mock(PlateDetectionService.class);
        vehicleMapper = mock(VehicleMapper.class);
        customerMapper = mock(CustomerMapper.class);
        blackListMapper = mock(BlackListMapper.class);
        reservationService = mock(ReservationService.class);
        service = new GateService();
        service.mapper = mapper;
        service.plateService = plateService;
        service.vehicleMapper = vehicleMapper;
        service.customerMapper = customerMapper;
        service.blackListMapper = blackListMapper;
        service.reservationService = reservationService;
    }

    @Test
    void handleByDeviceRoutesEntryAndExitAliases() {
        GateService spy = spy(service);
        PlateDetectionRequestDTO entryRequest = request(1);
        PlateDetectionRequestDTO exitRequest = request(2);
        GateResponseDTO entryResponse = new GateResponseDTO();
        GateResponseDTO exitResponse = new GateResponseDTO();
        when(mapper.selectDeviceTypeById(1)).thenReturn("ENTRY_CAMERA");
        when(mapper.selectDeviceTypeById(2)).thenReturn("EXIT");
        doReturn(entryResponse).when(spy).enter(entryRequest);
        doReturn(exitResponse).when(spy).exit(exitRequest);

        assertSame(entryResponse, spy.handleByDevice(entryRequest));
        assertSame(exitResponse, spy.handleByDevice(exitRequest));
    }

    @Test
    void enterRejectsWrongDeviceBeforeDetection() {
        when(mapper.selectDeviceTypeById(2)).thenReturn("EXIT_CAMERA");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.enter(request(2))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(plateService);
    }

    @Test
    void enterKeepsBarrierClosedWhenDetectionFails() {
        when(mapper.selectDeviceTypeById(1)).thenReturn("ENTRY_CAMERA");
        when(plateService.detectAndSave(any())).thenReturn(detection(false, null));

        GateResponseDTO result = service.enter(request(1));

        assertFalse(result.getBarrierOpen());
        assertEquals("ENTRY_DENIED", result.getEventType());
        verify(mapper, never()).insertEntry(any(), anyInt());
    }

    @Test
    void enterRejectsBlacklistedVehicle() {
        prepareEntryDetection();
        when(blackListMapper.findBlacklistsByPlateNumber("AB123456"))
                .thenReturn(new VehicleBlackListVO());

        GateResponseDTO result = service.enter(request(1));

        assertFalse(result.getBarrierOpen());
        assertEquals("ENTRY_DENIED_BLACKLIST", result.getEventType());
    }

    @Test
    void enterReturnsExistingOpenEntryWithoutDuplicate() {
        prepareEntryDetection();
        EntryExitVO existing = new EntryExitVO();
        existing.setEntryExitId(77);
        when(mapper.findLatestOpenEntryByPlateNumber("AB123456", 1))
                .thenReturn(existing);

        GateResponseDTO result = service.enter(request(1));

        assertTrue(result.getBarrierOpen());
        assertEquals(77, result.getEntryExitId());
        assertEquals("ENTRY_ALREADY_OPEN", result.getEventType());
        verify(mapper, never()).insertEntry(any(), anyInt());
    }

    @Test
    void enterCreatesReservedMemberEntry() {
        prepareEntryDetection();
        VehicleVO vehicle = vehicle(10, 20);
        when(vehicleMapper.findByPlateNumber("AB123456")).thenReturn(vehicle);
        when(customerMapper.getCustomerStatusFromVehicle(10)).thenReturn("ACTIVE");
        when(mapper.selectWashSiteIdByDeviceId(1)).thenReturn(1L);
        when(reservationService.checkArrivalReservation(eq(1L), eq("AB123456"), any()))
                .thenReturn(arrival(30L, 8L));
        when(mapper.insertEntry(any(), eq(1))).thenReturn(88);

        GateResponseDTO result = service.enter(request(1));

        assertTrue(result.getBarrierOpen());
        assertEquals("ENTRY_OPEN", result.getEventType());
        assertEquals(88, result.getEntryExitId());
        assertEquals(20, result.getCustomerId());
        assertEquals(30L, result.getReservationId());
    }

    @Test
    void enterAllowsPaidGuestReservation() {
        prepareEntryDetection();
        when(mapper.selectWashSiteIdByDeviceId(1)).thenReturn(1L);
        ReservationArrivalCheckDTO arrival = arrival(31L, 5L);
        arrival.setReservationType("GUEST");
        arrival.setPaymentStatus("PAID");
        when(reservationService.checkArrivalReservation(eq(1L), eq("AB123456"), any()))
                .thenReturn(arrival);
        when(mapper.insertEntry(any(), eq(1))).thenReturn(89);

        GateResponseDTO result = service.enter(request(1));

        assertTrue(result.getBarrierOpen());
        assertEquals("NON_MEMBER", result.getCustomerType());
        assertNull(result.getCustomerId());
        assertEquals(31L, result.getReservationId());
    }

    @Test
    void enterAllowsWalkInForEveryNonMatchedReservationStatus() {
        for (ReservationArrivalStatus status : new ReservationArrivalStatus[]{
                ReservationArrivalStatus.NOT_FOUND,
                ReservationArrivalStatus.TOO_EARLY,
                ReservationArrivalStatus.TOO_LATE
        }) {
            prepareEntryDetection();
            when(mapper.selectWashSiteIdByDeviceId(1)).thenReturn(1L);
            ReservationArrivalCheckDTO arrival = new ReservationArrivalCheckDTO();
            arrival.setStatus(status);
            arrival.setMessage("walk-in");
            when(reservationService.checkArrivalReservation(
                    eq(1L),
                    eq("AB123456"),
                    any()
            )).thenReturn(arrival);
            when(reservationService.findAvailableDeviceForArrival(
                    eq(1L),
                    any(),
                    any(),
                    any()
            )).thenReturn(5L);
            when(mapper.insertEntry(any(), eq(1))).thenReturn(90);

            GateResponseDTO result = service.enter(request(1));

            assertTrue(result.getBarrierOpen());
            assertEquals("ENTRY_OPEN", result.getEventType());
            assertEquals("NON_MEMBER", result.getCustomerType());
            assertFalse(result.getReservationMatched());
            assertEquals(status.name(), result.getReservationStatus());
        }
    }

    @Test
    void enterKeepsBarrierClosedWhenNoWashDeviceIsAvailable() {
        prepareEntryDetection();
        when(mapper.selectWashSiteIdByDeviceId(1)).thenReturn(1L);
        ReservationArrivalCheckDTO arrival = new ReservationArrivalCheckDTO();
        arrival.setStatus(ReservationArrivalStatus.NOT_FOUND);
        when(reservationService.checkArrivalReservation(
                eq(1L),
                eq("AB123456"),
                any()
        )).thenReturn(arrival);
        when(reservationService.findAvailableDeviceForArrival(
                eq(1L),
                any(),
                any(),
                any()
        )).thenReturn(null);

        GateResponseDTO result = service.enter(request(1));

        assertFalse(result.getBarrierOpen());
        assertEquals("ENTRY_DENIED_NO_DEVICE", result.getEventType());
        verify(mapper, never()).insertEntry(any(), anyInt());
    }

    @Test
    void enterAllowsGuestReservationBeforePaymentCompletes() {
        prepareEntryDetection();
        when(mapper.selectWashSiteIdByDeviceId(1)).thenReturn(1L);
        ReservationArrivalCheckDTO arrival = arrival(32L, 5L);
        arrival.setReservationType("GUEST");
        arrival.setPaymentStatus("PENDING");
        when(reservationService.checkArrivalReservation(
                eq(1L),
                eq("AB123456"),
                any()
        )).thenReturn(arrival);
        when(mapper.insertEntry(any(), eq(1))).thenReturn(91);

        GateResponseDTO result = service.enter(request(1));

        assertTrue(result.getBarrierOpen());
        assertEquals("ENTRY_OPEN", result.getEventType());
        assertEquals("NON_MEMBER", result.getCustomerType());
        assertEquals(32L, result.getReservationId());
    }

    @Test
    void exitRejectsMissingOpenEntry() {
        when(mapper.selectDeviceTypeById(2)).thenReturn("EXIT");
        when(plateService.detectAndSave(any())).thenReturn(detection(true, "AB123456"));
        when(mapper.findLatestOpenEntryByPlateNumber("AB123456", 2)).thenReturn(null);

        GateResponseDTO result = service.exit(request(2));

        assertFalse(result.getBarrierOpen());
        assertEquals("EXIT_DENIED", result.getEventType());
    }

    @Test
    void exitOpensForPaidWash() {
        EntryExitVO entry = new EntryExitVO();
        entry.setEntryExitId(30);
        when(mapper.selectDeviceTypeById(2)).thenReturn("EXIT");
        when(plateService.detectAndSave(any())).thenReturn(detection(true, "AB123456"));
        when(mapper.findLatestOpenEntryByPlateNumber("AB123456", 2)).thenReturn(entry);
        when(mapper.findOpenWashHistoryIdByEntryExitId(30)).thenReturn(40);
        when(mapper.selectWashStatus(40)).thenReturn("WASH_DONE");
        when(mapper.selectPaymentStatus(40)).thenReturn("DONE");
        when(mapper.updateEntryExitExit(30, 101)).thenReturn(1);
        when(mapper.updateWashExit(40)).thenReturn(1);

        GateResponseDTO result = service.exit(request(2));

        assertTrue(result.getBarrierOpen());
        assertEquals("EXITED", result.getWashStatus());
    }

    @Test
    void exitKeepsBarrierClosedForUnpaidCompletedWash() {
        EntryExitVO entry = new EntryExitVO();
        entry.setEntryExitId(30);
        when(mapper.selectDeviceTypeById(2)).thenReturn("EXIT");
        when(plateService.detectAndSave(any()))
                .thenReturn(detection(true, "AB123456"));
        when(mapper.findLatestOpenEntryByPlateNumber("AB123456", 2))
                .thenReturn(entry);
        when(mapper.findOpenWashHistoryIdByEntryExitId(30)).thenReturn(40);
        when(mapper.selectWashStatus(40)).thenReturn("WASH_DONE");
        when(mapper.selectPaymentStatus(40)).thenReturn("WAITING");

        GateResponseDTO result = service.exit(request(2));

        assertFalse(result.getBarrierOpen());
        assertEquals("EXIT_DENIED", result.getEventType());
        assertEquals("WAITING", result.getPaymentStatus());
        verify(mapper, never()).updateEntryExitExit(anyInt(), anyInt());
        verify(mapper, never()).updateWashExit(anyInt());
    }

    private void prepareEntryDetection() {
        when(mapper.selectDeviceTypeById(1)).thenReturn("ENTRY");
        when(plateService.detectAndSave(any())).thenReturn(detection(true, "AB123456"));
    }

    private PlateDetectionRequestDTO request(int deviceId) {
        PlateDetectionRequestDTO request = new PlateDetectionRequestDTO();
        request.setDeviceId(deviceId);
        return request;
    }

    private PlateDetectionResponseDTO detection(boolean success, String plate) {
        PlateDetectionResponseDTO result = new PlateDetectionResponseDTO();
        result.setPlateDetectionLogId(101);
        result.setDetectedPlateNumber(plate);
        result.setSuccess(success);
        return result;
    }

    private VehicleVO vehicle(int vehicleId, int customerId) {
        VehicleVO vehicle = new VehicleVO();
        vehicle.setVehicleId(vehicleId);
        vehicle.setCustomerId(customerId);
        return vehicle;
    }

    private ReservationArrivalCheckDTO arrival(Long reservationId, Long deviceId) {
        ReservationArrivalCheckDTO result = new ReservationArrivalCheckDTO();
        result.setStatus(ReservationArrivalStatus.MATCHED);
        result.setReservationId(reservationId);
        result.setCourseId(5L);
        result.setAssignedDeviceId(deviceId);
        return result;
    }
}
