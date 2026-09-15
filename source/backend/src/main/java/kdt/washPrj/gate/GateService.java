package kdt.washPrj.gate;

import jakarta.annotation.Resource;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;

@Service
public class GateService {
    private static final String PAYMENT_SUCCESS = "SUCCESS";

    @Resource
    GateMapper mapper;
    @Resource
    PlateDetectionService plateService;
    @Resource
    VehicleMapper vehicleMapper;
    @Resource
    CustomerMapper customerMapper;
    @Resource
    BlackListMapper blackListMapper;
    @Resource
    ReservationService reservationService;


    //장비 타입에 따라 입차/출차/세차 이벤트를 분기하여 처리
    @Transactional
    public GateResponseDTO handleByDevice(PlateDetectionRequestDTO request) {

        String deviceType =
                mapper.selectDeviceTypeById(request.getDeviceId());

        if ("ENTRY_CAMERA".equals(deviceType) || "ENTRY".equals(deviceType)) {
            return enter(request);
        }

        if ("EXIT_CAMERA".equals(deviceType) || "EXIT".equals(deviceType)) {
            return exit(request);
        }
        // WASH_CAMERA는 이후 WashCycleService로 연결
        if ("WASH_CAMERA".equals(deviceType) || "WASH".equals(deviceType)) {
            GateResponseDTO response = new GateResponseDTO();
            response.setBarrierOpen(false);
            response.setEventType("WRONG_ENDPOINT");
            response.setMessage("세차 카메라 요청은 /api/wash-cycles/detect로 요청해야 합니다.");
            return response;
        }

        GateResponseDTO response = new GateResponseDTO();
        response.setBarrierOpen(false);
        response.setMessage("지원하지 않는 장비 타입입니다.");
        return response;
    }
    //입차카메라 매소드
    @Transactional
    public  GateResponseDTO enter(PlateDetectionRequestDTO request){

        requireDeviceType(
                request.getDeviceId(),
                "ENTRY_CAMERA",
                "ENTRY"
        );

        PlateDetectionResponseDTO detection =
                plateService.detectAndSave(request);

        GateResponseDTO response =
                GateResponseDTO.gateFromDetection(detection);

        if (isDetectionFailed(detection)) {
            response.setBarrierOpen(false);
            response.setEventType("ENTRY_DENIED");
            response.setMessage("번호판 인식에 실패했습니다.");
            return response;
        }

        String plateNumber =
                detection.getDetectedPlateNumber();

        VehicleBlackListVO blacklist =
                blackListMapper.findBlacklistsByPlateNumber(
                        plateNumber
                );

        if (blacklist != null) {
            response.setMatchedVehicle(false);
            response.setBarrierOpen(false);
            response.setEventType(
                    "ENTRY_DENIED_BLACKLIST"
            );
            response.setMessage(
                    "입차가 제한된 차량입니다."
            );

            return response;
        }

        EntryExitVO existingEntry =
                mapper.findLatestOpenEntryByPlateNumber(
                        detection.getDetectedPlateNumber(),
                        request.getDeviceId()
                );

        if (existingEntry != null) {
            response.setEntryExitId(
                    existingEntry.getEntryExitId()
            );
            response.setBarrierOpen(true);
            response.setEventType("ENTRY_ALREADY_OPEN");
            response.setMessage(
                    "이미 입차 처리된 차량입니다."
            );

            return response;
        }

        VehicleVO vehicle =
                vehicleMapper.findByPlateNumber(
                        detection.getDetectedPlateNumber()
                );
        boolean matchedVehicle = vehicle !=null;
        if (matchedVehicle){
            String customerStatus =
                    customerMapper.getCustomerStatusFromVehicle(vehicle.getVehicleId());
            if ("SUSPENDED".equals(customerStatus)) {
                response.setMatchedVehicle(true);
                response.setVehicleId(vehicle.getVehicleId());
                response.setCustomerId(vehicle.getCustomerId());
                response.setCustomerType("MEMBER");
                response.setBarrierOpen(false);
                response.setEventType(
                        "ENTRY_DENIED_SUSPENDED"
                );
                response.setMessage(
                        "정지회원의 차량은 입차할 수 없습니다."
                );

                return response;
            }
        }

        Long washSiteId =
                mapper.selectWashSiteIdByDeviceId(request.getDeviceId());

        if (washSiteId == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "입구 카메라의 세차장 정보를 찾을 수 없습니다."
            );
        }

        LocalDateTime arrivalTime = LocalDateTime.now();
        ReservationArrivalCheckDTO arrivalCheck =
                reservationService.checkArrivalReservation(
                        washSiteId,
                        detection.getDetectedPlateNumber(),
                        arrivalTime
                );

        applyReservationResult(response, arrivalCheck);

        Long assignedDeviceId = arrivalCheck.getAssignedDeviceId();
        if (assignedDeviceId == null) {
            assignedDeviceId = reservationService.findAvailableDeviceForArrival(
                    washSiteId,
                    arrivalCheck.getReservationId(),
                    arrivalCheck.getReservedDeviceId(),
                    arrivalTime
            );
            response.setAssignedDeviceId(assignedDeviceId);
        }

        if (assignedDeviceId == null) {
            response.setMatchedVehicle(matchedVehicle);
            if (matchedVehicle) {
                response.setVehicleId(vehicle.getVehicleId());
                response.setCustomerId(vehicle.getCustomerId());
            }
            response.setCustomerType(
                    matchedVehicle ? "MEMBER" : "NON_MEMBER"
            );
            response.setBarrierOpen(false);
            response.setEventType("ENTRY_DENIED_NO_DEVICE");
            response.setMessage("현재 이용 가능한 세차기가 없습니다.");
            return response;
        }

        boolean reservationMatched = arrivalCheck.getStatus()
                == ReservationArrivalStatus.MATCHED;
        boolean guestReservation = reservationMatched && "GUEST".equals(
                arrivalCheck.getReservationType()
        );


        EntryExitVO entryExit = new EntryExitVO();
        entryExit.setPlateNumber(detection.getDetectedPlateNumber());
        entryExit.setEntryDetectionLogId(detection.getPlateDetectionLogId());
        if (matchedVehicle && !guestReservation) {
            entryExit.setCustomerId(vehicle.getCustomerId());
            entryExit.setVehicleId(vehicle.getVehicleId());
        }

        Integer entryExitId = mapper.insertEntry(entryExit, request.getDeviceId());
        if (entryExitId == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "입차 장비에 연결된 세차장을 확인할 수 없습니다."
            );
        }
        entryExit.setEntryExitId(entryExitId);

        mapper.insertGateEvent(
                entryExit.getEntryExitId(),
                null,
                request.getDeviceId(),
                "ENTRY_OPEN",
                entryReason(reservationMatched, guestReservation, matchedVehicle)
        );

        response.setEntryExitId(entryExit.getEntryExitId());
        response.setMatchedVehicle(matchedVehicle);
        response.setBarrierOpen(true);
        response.setEventType("ENTRY_OPEN");
        response.setMessage(
                reservationMatched
                        ? arrivalCheck.getMessage()
                        : "현장 이용 차량으로 입차합니다."
        );

        if (matchedVehicle && !guestReservation) {
            response.setVehicleId(vehicle.getVehicleId());
            response.setCustomerId(vehicle.getCustomerId());
            response.setCustomerType("MEMBER");
        } else {
            response.setCustomerType("NON_MEMBER");
        }

        return response;

    }

    @Transactional
    public GateResponseDTO exit(PlateDetectionRequestDTO request) {

        requireDeviceType(
                request.getDeviceId(),
                "EXIT_CAMERA",
                "EXIT"
        );

        PlateDetectionResponseDTO detection =
                plateService.detectAndSave(request);

        GateResponseDTO response =
                GateResponseDTO.gateFromDetection(detection);

        if (isDetectionFailed(detection)) {
            response.setBarrierOpen(false);
            response.setEventType("EXIT_DENIED");
            response.setMessage("번호판 인식에 실패했습니다.");
            return response;
        }

        EntryExitVO entryExit =
                mapper.findLatestOpenEntryByPlateNumber(
                        detection.getDetectedPlateNumber(),
                        request.getDeviceId()
                );

        if (entryExit == null) {
            response.setBarrierOpen(false);
            response.setEventType("EXIT_DENIED");
            response.setMessage("입차 기록이 없습니다.");
            return response;
        }

        Integer washHistoryId =
                mapper.findOpenWashHistoryIdByEntryExitId(
                        entryExit.getEntryExitId()
                );

        response.setEntryExitId(entryExit.getEntryExitId());
        response.setWashHistoryId(washHistoryId);

        // 회차 차량: 입차 기록은 있지만 세차 기록은 없음
        if (washHistoryId == null) {
            int entryExited = mapper.updateEntryExitExit(
                    entryExit.getEntryExitId(),
                    detection.getPlateDetectionLogId()
            );

            if (entryExited == 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "출차 상태가 이미 변경되었습니다."
                );
            }

            mapper.insertGateEvent(
                    entryExit.getEntryExitId(),
                    null,
                    request.getDeviceId(),
                    "EXIT_OPEN",
                    "세차 기록 없는 회차 차량 출차 허용"
            );

            response.setBarrierOpen(true);
            response.setEventType("EXIT_OPEN");
            response.setMessage("회차 차량 출차 완료");

            return response;
        }
        String washStatus =
                mapper.selectWashStatus(washHistoryId);

        response.setWashStatus(washStatus);
        if (!"WASH_DONE".equals(washStatus)) {
            mapper.insertGateEvent(
                    entryExit.getEntryExitId(),
                    washHistoryId,
                    request.getDeviceId(),
                    "EXIT_DENIED",
                    "세차 미완료"
            );

            response.setBarrierOpen(false);
            response.setEventType("EXIT_DENIED");
            response.setMessage(
                    "세차가 완료되지 않아 출차할 수 없습니다."
            );

            return response;
        }


        String paymentStatus =
                mapper.selectPaymentStatus(washHistoryId);

        response.setPaymentStatus(paymentStatus);


        if (!isPaymentSuccess(paymentStatus)) {
            mapper.insertGateEvent(
                    entryExit.getEntryExitId(),
                    washHistoryId,
                    request.getDeviceId(),
                    "EXIT_DENIED",
                    "결제 미완료"
            );

            response.setBarrierOpen(false);
            response.setEventType("EXIT_DENIED");
            response.setMessage("결제가 완료되지 않았습니다.");

            return response;
        }

        int entryExited = mapper.updateEntryExitExit(
                entryExit.getEntryExitId(),
                detection.getPlateDetectionLogId()
        );

        int washExited = mapper.updateWashExit(washHistoryId);

        if (entryExited == 0 || washExited == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "출차 상태가 이미 변경되었거나 처리 조건을 충족하지 않습니다."
            );
        }

        mapper.insertGateEvent(
                entryExit.getEntryExitId(),
                washHistoryId,
                request.getDeviceId(),
                "EXIT_OPEN",
                "결제 완료 차량 출차 허용"
        );

        response.setBarrierOpen(true);
        response.setEventType("EXIT_OPEN");
        response.setWashStatus("EXITED");
        response.setMessage("출차 완료");

        return response;
    }

    private void applyReservationResult(
            GateResponseDTO response,
            ReservationArrivalCheckDTO arrivalCheck
    ) {
        response.setReservationMatched(
                arrivalCheck.getStatus()
                        == ReservationArrivalStatus.MATCHED
        );
        response.setReservationId(arrivalCheck.getReservationId());
        response.setReservationStatus(arrivalCheck.getStatus().name());
        response.setReservedDeviceId(arrivalCheck.getReservedDeviceId());
        response.setAssignedDeviceId(arrivalCheck.getAssignedDeviceId());
    }

    private String entryReason(
            boolean reservationMatched,
            boolean guestReservation,
            boolean matchedVehicle
    ) {
        if (guestReservation) {
            return "GUEST_RESERVATION";
        }
        if (reservationMatched) {
            return "MEMBER_RESERVATION";
        }
        return matchedVehicle ? "MEMBER_WALK_IN" : "NON_MEMBER_WALK_IN";
    }


    private boolean isDetectionFailed(PlateDetectionResponseDTO detection) {
        return !Boolean.TRUE.equals(detection.getSuccess())
                || detection.getDetectedPlateNumber() == null
                || detection.getDetectedPlateNumber().isBlank();
    }

    private boolean isPaymentSuccess(String paymentStatus) {
        return PAYMENT_SUCCESS.equals(paymentStatus)
                || "DONE".equals(paymentStatus);
    }

    private void requireDeviceType(
            Integer deviceId,
            String... allowedTypes
    ) {
        String deviceType =
                mapper.selectDeviceTypeById(deviceId);

        if (deviceType == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "장비를 찾을 수 없습니다."
            );
        }

        boolean matched = Arrays.stream(allowedTypes)
                .anyMatch(type -> type.equals(deviceType));

        if (!matched) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "요청 API와 장비 유형이 일치하지 않습니다."
            );
        }
    }
}


