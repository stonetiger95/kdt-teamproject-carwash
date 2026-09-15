package kdt.washPrj.washCycle;

import kdt.washPrj.gate.EntryExitVO;
import kdt.washPrj.gate.GateMapper;
import kdt.washPrj.prepaid.PrepaidService;
import kdt.washPrj.prepaid.dto.PrepaidUseResponseDTO;
import kdt.washPrj.plateDetection.PlateDetectionMapper;
import kdt.washPrj.plateDetection.PlateDetectionService;
import kdt.washPrj.plateDetection.dto.PlateDetectionRequestDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionResponseDTO;
import kdt.washPrj.reservation.ReservationArrivalStatus;
import kdt.washPrj.reservation.ReservationService;
import kdt.washPrj.reservation.dto.ReservationArrivalCheckDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WashCycleServiceTest {
    private PlateDetectionService plateService;
    private PrepaidService prepaidService;
    private GateMapper gateMapper;
    private WashCycleMapper washCycleMapper;
    private PlateDetectionMapper plateMapper;
    private ReservationService reservationService;
    private WashCycleService service;

    @BeforeEach
    void setUp() {
        plateService = mock(PlateDetectionService.class);
        prepaidService = mock(PrepaidService.class);
        gateMapper = mock(GateMapper.class);
        washCycleMapper = mock(WashCycleMapper.class);
        plateMapper = mock(PlateDetectionMapper.class);
        reservationService = mock(ReservationService.class);
        service = new WashCycleService();
        service.plateService = plateService;
        service.prepaidService = prepaidService;
        service.gateMapper = gateMapper;
        service.washCycleMapper = washCycleMapper;
        service.plateMapper = plateMapper;
        service.reservationService = reservationService;
    }

    @Test
    void processWashCameraRejectsWrongDeviceBeforeDetection() {
        when(gateMapper.selectDeviceTypeById(2)).thenReturn("ENTRY_CAMERA");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.processWashCamera(request(2)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(plateService);
    }

    @Test
    void processWashCameraDeniesFailedPlateDetection() {
        when(gateMapper.selectDeviceTypeById(2)).thenReturn("WASH_CAMERA");
        when(plateService.detectAndSave(any())).thenReturn(detection(false));

        WashCycleResponseDTO result = service.processWashCamera(request(2));

        assertFalse(result.getAccepted());
        assertEquals("WASH_DENIED", result.getEventType());
        verify(gateMapper, never()).findLatestOpenEntryByPlateNumber(any(), anyInt());
    }

    @Test
    void processWashCameraDeniesMissingEntrySiteOrReservationCourse() {
        prepareDetectedRequest();
        when(gateMapper.findLatestOpenEntryByPlateNumber("12가3456", 2)).thenReturn(null);
        assertEquals("WASH_DENIED", service.processWashCamera(request(2)).getEventType());

        EntryExitVO entry = entry(10, 20);
        when(gateMapper.findLatestOpenEntryByPlateNumber("12가3456", 2)).thenReturn(entry);
        when(washCycleMapper.selectWashSiteIdByDeviceId(2)).thenReturn(null);
        assertEquals("WASH_DENIED", service.processWashCamera(request(2)).getEventType());

        when(washCycleMapper.selectWashSiteIdByDeviceId(2)).thenReturn(3);
        when(reservationService.checkArrivalReservation(
                eq(3L), eq("12가3456"), any(), any()
        )).thenReturn(arrival(
                ReservationArrivalStatus.MATCHED,
                2L,
                null,
                12000
        ));
        assertEquals("WASH_DENIED", service.processWashCamera(request(2)).getEventType());
        verify(washCycleMapper, never()).createWashHistoryFromEntry(
                anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString(), nullable(Long.class),
                nullable(Integer.class));
    }

    @Test
    void processWashCameraDeniesExistingWashFromAnotherSite() {
        prepareDetectedRequest();
        when(gateMapper.findLatestOpenEntryByPlateNumber("12가3456", 2)).thenReturn(entry(10, null));
        when(washCycleMapper.selectWashSiteIdByDeviceId(2)).thenReturn(3);
        when(gateMapper.findOpenWashHistoryIdByEntryExitId(10)).thenReturn(50);
        when(washCycleMapper.selectWashCycleState(50)).thenReturn(state(50, 4, "NON_MEMBER", "WAITING", "ENTERED"));

        WashCycleResponseDTO result = service.processWashCamera(request(2));

        assertFalse(result.getAccepted());
        assertEquals(50, result.getWashHistoryId());
        verify(washCycleMapper, never()).startWashIfAllowed(anyInt());
    }

    @Test
    void processWashCameraReturnsAlreadyExistsForStartedWash() {
        prepareExisting(state(50, 3, "MEMBER", "DONE", "WASHING"));

        WashCycleResponseDTO result = service.processWashCamera(request(2));

        assertEquals("WASH_ALREADY_EXISTS", result.getEventType());
        assertFalse(result.getAccepted());
    }

    @Test
    void processWashCameraAutoPaysMemberOrReturnsPaymentRequired() {
        WashCycleStateDTO member = state(50, 3, "MEMBER", "WAITING", "ENTERED");
        member.setCustomerId(20);
        prepareExisting(member);
        PrepaidUseResponseDTO paid = new PrepaidUseResponseDTO();
        paid.setStatus("SUCCESS");
        paid.setAmount(7000);
        paid.setBalance(3000);
        when(prepaidService.autoUseWithBillingTopUp(20L, 50L)).thenReturn(paid);
        when(washCycleMapper.startWashIfAllowed(50)).thenReturn(1);

        WashCycleResponseDTO success = service.processWashCamera(request(2));
        assertTrue(success.getAccepted());
        assertTrue(success.getAutoPaymentSuccess());
        assertEquals(7000, success.getPaidAmount());

        member.setPaymentStatus("WAITING");
        when(prepaidService.autoUseWithBillingTopUp(20L, 50L)).thenReturn(null);
        WashCycleResponseDTO denied = service.processWashCamera(request(2));
        assertFalse(denied.getAccepted());
        assertEquals("PAYMENT_REQUIRED", denied.getWashStatus());
    }

    @Test
    void processWashCameraStartsPaidMemberAndWaitingNonMember() {
        WashCycleStateDTO paidMember = state(50, 3, "MEMBER", "DONE", "ENTERED");
        paidMember.setCustomerId(20);
        prepareExisting(paidMember);
        when(washCycleMapper.startWashIfAllowed(50)).thenReturn(1);
        assertTrue(service.processWashCamera(request(2)).getAccepted());
        verifyNoInteractions(prepaidService);

        reset(prepaidService);
        prepareExisting(state(50, 3, "NON_MEMBER", "WAITING", "ENTERED"));
        when(washCycleMapper.startWashIfAllowed(50)).thenReturn(1);
        WashCycleResponseDTO waitingNonMember =
                service.processWashCamera(request(2));
        assertTrue(waitingNonMember.getAccepted());
        assertEquals("WAITING", waitingNonMember.getPaymentStatus());
        assertFalse(waitingNonMember.getAutoPaymentSuccess());
        verifyNoInteractions(prepaidService);
    }

    @Test
    void processWashCameraCreatesReservedMemberHistoryAndLinksDetection() {
        prepareDetectedRequest();
        EntryExitVO entry = entry(10, 20);
        when(gateMapper.findLatestOpenEntryByPlateNumber("12가3456", 2)).thenReturn(entry);
        when(gateMapper.findOpenWashHistoryIdByEntryExitId(10)).thenReturn(null);
        when(washCycleMapper.selectWashSiteIdByDeviceId(2)).thenReturn(3);
        when(reservationService.checkArrivalReservation(
                eq(3L), eq("12가3456"), any(), any()
        )).thenReturn(arrival(
                ReservationArrivalStatus.MATCHED,
                2L,
                4L,
                7000
        ));
        when(washCycleMapper.createWashHistoryFromEntry(
                anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString(), nullable(Long.class),
                nullable(Integer.class))).thenReturn(50);
        when(washCycleMapper.selectWashCycleState(50))
                .thenReturn(state(50, 3, "MEMBER", "DONE", "ENTERED"));
        when(washCycleMapper.startWashIfAllowed(50)).thenReturn(1);

        WashCycleResponseDTO result = service.processWashCamera(request(2));

        assertTrue(result.getAccepted(),
                "event=" + result.getEventType() + ", message=" + result.getMessage()
                        + ", washHistoryId=" + result.getWashHistoryId());
        assertEquals("WASH_CREATED", result.getEventType());
        assertEquals(2, result.getDeviceId());
        verify(plateMapper).updateWashHistoryId(100, 50);
    }

    @Test
    void processWashCameraDeniesMissingCreatedStateOrFailedStart() {
        prepareDetectedRequest();
        EntryExitVO entry = entry(10, 20);
        when(gateMapper.findLatestOpenEntryByPlateNumber("12가3456", 2)).thenReturn(entry);
        when(gateMapper.findOpenWashHistoryIdByEntryExitId(10)).thenReturn(null);
        when(washCycleMapper.selectWashSiteIdByDeviceId(2)).thenReturn(3);
        when(reservationService.checkArrivalReservation(
                eq(3L), eq("12가3456"), any(), any()
        )).thenReturn(arrival(
                ReservationArrivalStatus.MATCHED,
                2L,
                4L,
                7000
        ));
        when(washCycleMapper.createWashHistoryFromEntry(
                anyInt(), eq(3), eq(2), eq(4), anyString(), anyString(), nullable(Long.class),
                nullable(Integer.class))).thenReturn(50);
        when(washCycleMapper.selectWashCycleState(50)).thenReturn(null);
        assertEquals("WASH_DENIED", service.processWashCamera(request(2)).getEventType());

        when(gateMapper.findOpenWashHistoryIdByEntryExitId(10)).thenReturn(50);
        when(washCycleMapper.selectWashCycleState(50))
                .thenReturn(state(50, 3, "MEMBER", "DONE", "ENTERED"));
        when(washCycleMapper.startWashIfAllowed(50)).thenReturn(0);
        assertEquals("WASH_DENIED", service.processWashCamera(request(2)).getEventType());
    }

    @Test
    void processWashCameraUsesReservedCoursePriceAndReservationId() {
        prepareDetectedRequest();
        EntryExitVO entry = entry(10, 20);
        when(gateMapper.findLatestOpenEntryByPlateNumber("12가3456", 2)).thenReturn(entry);
        when(gateMapper.findOpenWashHistoryIdByEntryExitId(10)).thenReturn(null);
        when(washCycleMapper.selectWashSiteIdByDeviceId(2)).thenReturn(3);

        when(reservationService.checkArrivalReservation(
                eq(3L), eq("12가3456"), any(), any()
        )).thenReturn(arrival(
                ReservationArrivalStatus.MATCHED,
                2L,
                8L,
                12000
        ));

        when(washCycleMapper.createWashHistoryFromEntry(
                anyInt(), eq(3), eq(2), eq(8), eq("MEMBER"), eq("WAITING"), eq(99L), eq(12000)))
                .thenReturn(50);
        WashCycleStateDTO createdState = state(50, 3, "MEMBER", "DONE", "ENTERED");
        createdState.setCustomerId(20);
        when(washCycleMapper.selectWashCycleState(50)).thenReturn(createdState);
        when(washCycleMapper.startWashIfAllowed(50)).thenReturn(1);

        WashCycleResponseDTO result = service.processWashCamera(request(2));

        assertTrue(result.getAccepted());
        verify(washCycleMapper).createWashHistoryFromEntry(
                eq(entry.getEntryExitId()), eq(3), eq(2), eq(8), eq("MEMBER"), eq("WAITING"), eq(99L), eq(12000));
        verify(reservationService).completeReservation(99L);
        assertEquals("FULFILLED", result.getReservationStatus());
    }

    @Test
    void processWashCameraKeepsReservationConfirmedWhenPaymentFails() {
        prepareDetectedRequest();
        EntryExitVO entry = entry(10, 20);
        when(gateMapper.findLatestOpenEntryByPlateNumber("12가3456", 2))
                .thenReturn(entry);
        when(gateMapper.findOpenWashHistoryIdByEntryExitId(10))
                .thenReturn(null);
        when(washCycleMapper.selectWashSiteIdByDeviceId(2)).thenReturn(3);

        when(reservationService.checkArrivalReservation(
                eq(3L), eq("12가3456"), any(), any()
        )).thenReturn(arrival(
                ReservationArrivalStatus.MATCHED,
                2L,
                8L,
                12000
        ));

        when(washCycleMapper.createWashHistoryFromEntry(
                anyInt(), eq(3), eq(2), eq(8), eq("MEMBER"), eq("WAITING"), eq(99L), eq(12000)))
                .thenReturn(50);
        WashCycleStateDTO createdState =
                state(50, 3, "MEMBER", "WAITING", "ENTERED");
        createdState.setCustomerId(20);
        createdState.setReservationId(99L);
        when(washCycleMapper.selectWashCycleState(50))
                .thenReturn(createdState);
        when(prepaidService.autoUseWithBillingTopUp(20L, 50L))
                .thenReturn(null);

        WashCycleResponseDTO result = service.processWashCamera(request(2));

        assertFalse(result.getAccepted());
        assertEquals("PAYMENT_REQUIRED", result.getWashStatus());
        verify(reservationService, never()).completeReservation(anyLong());
    }

    @Test
    void processWashCameraRedirectsEarlyReservationWithoutTimeRejection() {
        prepareDetectedRequest();
        EntryExitVO entry = entry(10, 20);
        when(gateMapper.findLatestOpenEntryByPlateNumber("12가3456", 2))
                .thenReturn(entry);
        when(gateMapper.findOpenWashHistoryIdByEntryExitId(10))
                .thenReturn(null);
        when(washCycleMapper.selectWashSiteIdByDeviceId(2)).thenReturn(3);
        when(reservationService.checkArrivalReservation(
                eq(3L), eq("12가3456"), any(), any()
        )).thenReturn(arrival(
                ReservationArrivalStatus.TOO_EARLY,
                8L,
                4L,
                7000
        ));

        WashCycleResponseDTO result =
                service.processWashCamera(request(2));

        assertFalse(result.getAccepted());
        assertEquals("WASH_REDIRECT", result.getEventType());
        assertEquals(8L, result.getAssignedDeviceId());
        assertTrue(result.getReservationMatched());
        assertFalse(result.getMessage().contains("예약 30분 전부터"));
        verify(washCycleMapper, never())
                .createWashHistoryFromEntry(
                        anyInt(), anyInt(), anyInt(), anyInt(), anyString(),
                        anyString(), anyLong(), anyInt()
                );
    }

    @Test
    void processWashCameraCompletesReservationAfterSuccessfulRetry() {
        WashCycleStateDTO existing =
                state(50, 3, "MEMBER", "DONE", "ENTERED");
        existing.setCustomerId(20);
        existing.setReservationId(99L);
        prepareExisting(existing);
        when(washCycleMapper.startWashIfAllowed(50)).thenReturn(1);

        WashCycleResponseDTO result = service.processWashCamera(request(2));

        assertTrue(result.getAccepted());
        verify(reservationService).completeReservation(99L);
    }

    @Test
    void processWashCameraStartsPaidGuestReservationWithoutChargingWallet() {
        prepareDetectedRequest();
        EntryExitVO guestEntry = entry(10, null);
        when(gateMapper.findLatestOpenEntryByPlateNumber(anyString(), eq(2)))
                .thenReturn(guestEntry);
        when(gateMapper.findOpenWashHistoryIdByEntryExitId(10))
                .thenReturn(null);
        when(washCycleMapper.selectWashSiteIdByDeviceId(2)).thenReturn(3);

        ReservationArrivalCheckDTO guestArrival = arrival(
                ReservationArrivalStatus.MATCHED,
                2L,
                8L,
                7000
        );
        guestArrival.setReservationType("GUEST");
        guestArrival.setPaymentStatus("PAID");
        when(reservationService.checkArrivalReservation(
                eq(3L), anyString(), any(), any()
        )).thenReturn(guestArrival);

        when(washCycleMapper.createWashHistoryFromEntry(
                anyInt(), eq(3), eq(2), eq(8), eq("NON_MEMBER"),
                eq("SUCCESS"), eq(99L), eq(7000)
        )).thenReturn(51);

        WashCycleStateDTO createdState = state(
                51,
                3,
                "NON_MEMBER",
                "SUCCESS",
                "ENTERED"
        );
        when(washCycleMapper.selectWashCycleState(51))
                .thenReturn(createdState);
        when(washCycleMapper.startWashIfAllowed(51)).thenReturn(1);

        WashCycleResponseDTO result = service.processWashCamera(request(2));

        assertTrue(result.getAccepted());
        assertEquals("NON_MEMBER", result.getCustomerType());
        assertEquals("SUCCESS", result.getPaymentStatus());
        assertEquals("FULFILLED", result.getReservationStatus());
        verifyNoInteractions(prepaidService);
        verify(reservationService).completeReservation(99L);
    }

    @Test
    void processWashCameraStartsNonMemberWalkInBeforePayment() {
        prepareDetectedRequest();
        EntryExitVO walkInEntry = entry(10, null);
        when(gateMapper.findLatestOpenEntryByPlateNumber(anyString(), eq(2)))
                .thenReturn(walkInEntry);
        when(gateMapper.findOpenWashHistoryIdByEntryExitId(10))
                .thenReturn(null);
        when(washCycleMapper.selectWashSiteIdByDeviceId(2)).thenReturn(3);

        ReservationArrivalCheckDTO noReservation = noReservation();
        when(reservationService.checkArrivalReservation(
                eq(3L), anyString(), any(), any()
        )).thenReturn(noReservation);
        when(reservationService.findAvailableDeviceForArrival(
                eq(3L), isNull(), isNull(), any()
        )).thenReturn(2L);

        WalkInCourseDTO defaultCourse = new WalkInCourseDTO();
        defaultCourse.setCourseId(8);
        defaultCourse.setWashPrice(7000);
        when(washCycleMapper.selectDefaultWalkInCourse(3))
                .thenReturn(defaultCourse);

        when(washCycleMapper.createWashHistoryFromEntry(
                eq(walkInEntry.getEntryExitId()), eq(3), eq(2), eq(8),
                eq("NON_MEMBER"), eq("WAITING"), isNull(), eq(7000)
        )).thenReturn(52);

        WashCycleStateDTO createdState = state(
                52, 3, "NON_MEMBER", "WAITING", "ENTERED"
        );
        createdState.setReservationId(null);
        when(washCycleMapper.selectWashCycleState(52))
                .thenReturn(createdState);
        when(washCycleMapper.startWashIfAllowed(52)).thenReturn(1);

        WashCycleResponseDTO result = service.processWashCamera(request(2));

        assertTrue(result.getAccepted());
        assertEquals("WASH_CREATED", result.getEventType());
        assertEquals("WASHING", result.getWashStatus());
        assertEquals("WAITING", result.getPaymentStatus());
        assertFalse(result.getAutoPaymentSuccess());
        assertEquals(52, result.getWashHistoryId());
        assertNull(result.getReservationId());
        verify(plateMapper).updateWashHistoryId(100, 52);
        verify(reservationService, never()).completeReservation(anyLong());
    }

    @Test
    void processWashCameraStartsPaidNonMemberWalkInOnRetry() {
        WashCycleStateDTO paidWalkIn = state(
                52, 3, "NON_MEMBER", "SUCCESS", "ENTERED"
        );
        paidWalkIn.setReservationId(null);
        prepareExisting(paidWalkIn);
        when(washCycleMapper.startWashIfAllowed(52)).thenReturn(1);

        WashCycleResponseDTO result = service.processWashCamera(request(2));

        assertTrue(result.getAccepted());
        assertEquals("WASH_STARTED", result.getEventType());
        assertNull(result.getReservationId());
        verify(washCycleMapper).startWashIfAllowed(52);
        verify(reservationService, never()).completeReservation(anyLong());
    }

    @Test
    void processWashCameraDeniesWalkInWhenNoDeviceIsAvailable() {
        prepareDetectedRequest();
        when(gateMapper.findLatestOpenEntryByPlateNumber(anyString(), eq(2)))
                .thenReturn(entry(10, null));
        when(gateMapper.findOpenWashHistoryIdByEntryExitId(10))
                .thenReturn(null);
        when(washCycleMapper.selectWashSiteIdByDeviceId(2)).thenReturn(3);
        when(reservationService.checkArrivalReservation(
                eq(3L), anyString(), any(), any()
        )).thenReturn(noReservation());
        when(reservationService.findAvailableDeviceForArrival(
                eq(3L), isNull(), isNull(), any()
        )).thenReturn(null);

        WashCycleResponseDTO result = service.processWashCamera(request(2));

        assertFalse(result.getAccepted());
        assertEquals("WASH_DENIED_NO_DEVICE", result.getEventType());
        verify(washCycleMapper, never()).createWashHistoryFromEntry(
                anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString(),
                nullable(Long.class), nullable(Integer.class)
        );
    }

    private void prepareDetectedRequest() {
        when(gateMapper.selectDeviceTypeById(2)).thenReturn("WASH_CAMERA");
        when(plateService.detectAndSave(any())).thenReturn(detection(true));
    }

    private void prepareExisting(WashCycleStateDTO existing) {
        prepareDetectedRequest();
        when(gateMapper.findLatestOpenEntryByPlateNumber("12가3456", 2)).thenReturn(entry(10, existing.getCustomerId()));
        when(washCycleMapper.selectWashSiteIdByDeviceId(2)).thenReturn(3);
        when(gateMapper.findOpenWashHistoryIdByEntryExitId(10)).thenReturn(50);
        when(washCycleMapper.selectWashCycleState(50)).thenReturn(existing);
    }

    private PlateDetectionRequestDTO request(int deviceId) {
        PlateDetectionRequestDTO request = new PlateDetectionRequestDTO();
        request.setDeviceId(deviceId);
        return request;
    }

    private PlateDetectionResponseDTO detection(boolean success) {
        PlateDetectionResponseDTO response = new PlateDetectionResponseDTO();
        response.setPlateDetectionLogId(100);
        response.setDetectedPlateNumber(success ? "12가3456" : null);
        response.setSuccess(success);
        return response;
    }

    private EntryExitVO entry(int id, Integer customerId) {
        EntryExitVO entry = new EntryExitVO();
        entry.setEntryExitId(id);
        entry.setCustomerId(customerId);
        if (customerId != null) {
            entry.setVehicleId(30);
        }
        entry.setPlateNumber("12가3456");
        return entry;
    }

    private WashCycleStateDTO state(int id, int siteId, String customerType,
                                    String paymentStatus, String washStatus) {
        WashCycleStateDTO state = new WashCycleStateDTO();
        state.setWashHistoryId(id);
        state.setReservationId(99L);
        state.setDeviceId(2);
        state.setReservedDeviceId(2L);
        state.setReservationStatus("CONFIRMED");
        state.setWashSiteId(siteId);
        state.setCustomerType(customerType);
        state.setPaymentStatus(paymentStatus);
        state.setWashStatus(washStatus);
        return state;
    }

    private ReservationArrivalCheckDTO arrival(
            ReservationArrivalStatus status,
            Long assignedDeviceId,
            Long courseId,
            Integer price
    ) {
        ReservationArrivalCheckDTO result =
                new ReservationArrivalCheckDTO();
        result.setStatus(status);
        result.setReservationId(99L);
        result.setReservedDeviceId(2L);
        result.setAssignedDeviceId(assignedDeviceId);
        result.setCourseId(courseId);
        result.setReservationPrice(price);
        result.setMessage("예약 확인 결과");
        return result;
    }

    private ReservationArrivalCheckDTO noReservation() {
        ReservationArrivalCheckDTO result = new ReservationArrivalCheckDTO();
        result.setStatus(ReservationArrivalStatus.NOT_FOUND);
        result.setMessage("예약 없음");
        return result;
    }
}
