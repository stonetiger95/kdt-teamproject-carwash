package kdt.washPrj.washCycle;

import jakarta.annotation.Resource;
import kdt.washPrj.gate.EntryExitVO;
import kdt.washPrj.gate.GateMapper;
import kdt.washPrj.prepaid.PrepaidService;
import kdt.washPrj.prepaid.dto.PrepaidUseResponseDTO;
import kdt.washPrj.plateDetection.PlateDetectionMapper;
import kdt.washPrj.plateDetection.PlateDetectionService;
import kdt.washPrj.plateDetection.dto.PlateDetectionRequestDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionResponseDTO;
import kdt.washPrj.reservation.ReservationService;
import kdt.washPrj.reservation.dto.ReservationArrivalCheckDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class WashCycleService {
    private static final String CUSTOMER_MEMBER = "MEMBER";
    private static final String CUSTOMER_NON_MEMBER = "NON_MEMBER";
    private static final String PAYMENT_WAITING = "WAITING";
    private static final String PAYMENT_SUCCESS = "SUCCESS";
    private static final String PAYMENT_DONE = "DONE";
    private static final String WASH_ENTERED = "ENTERED";
    private static final String WASHING = "WASHING";

    @Resource
    PlateDetectionService plateService;
    @Resource
    PrepaidService prepaidService;
    @Resource
    GateMapper gateMapper;
    @Resource
    WashCycleMapper washCycleMapper;
    @Resource
    PlateDetectionMapper plateMapper;
    @Resource
    ReservationService reservationService;

    @Transactional
    public WashCycleResponseDTO processWashCamera(PlateDetectionRequestDTO request) {
        // 0. 장비 종류 확인
        requireWashCamera(request.getDeviceId());

        // 1. 번호판 처리
        PlateDetectionResponseDTO detection =
                plateService.detectAndSave(request);
        WashCycleResponseDTO response =
                WashCycleResponseDTO.washCycleFromDetection(detection);

        // 2. 인식 실패면 WASH_DENIED 반환
        if (isDetectionFailed(detection)) {
            response.setAccepted(false);
            response.setEventType("WASH_DENIED");
            response.setMessage("번호판 인식에 실패했습니다.");
            return response;
        }
        // 3. plateNumber로 열린 entry_exit 조회
        EntryExitVO entryExit =
                gateMapper.findLatestOpenEntryByPlateNumber(
                        detection.getDetectedPlateNumber(),
                        request.getDeviceId()
                );

        // 4. 열린 입차 기록 없으면 WASH_DENIED
        if (entryExit == null) {
            response.setAccepted(false);
            response.setEventType("WASH_DENIED");
            response.setMessage("입차 기록이 없어 세차를 시작할 수 없습니다.");
            return response;
        }

        response.setEntryExitId(entryExit.getEntryExitId());

        Integer washSiteId =
                washCycleMapper.selectWashSiteIdByDeviceId(
                        request.getDeviceId()
                );

        if (washSiteId == null) {
            response.setAccepted(false);
            response.setEventType("WASH_DENIED");
            response.setMessage("세차 카메라에 연결된 세차장 정보가 없습니다.");
            return response;
        }

        // 5. 이미 열린 wash_history 있으면 중복 생성 막고 기존 washHistoryId 반환
        Integer existingWashHistoryId =
                gateMapper.findOpenWashHistoryIdByEntryExitId(
                        entryExit.getEntryExitId()
                );
        if (existingWashHistoryId != null) {
            WashCycleStateDTO existingState =
                    washCycleMapper.selectWashCycleState(existingWashHistoryId);

            if (existingState == null
                    || !washSiteId.equals(existingState.getWashSiteId())) {
                response.setAccepted(false);
                response.setWashHistoryId(existingWashHistoryId);
                response.setEventType("WASH_DENIED");
                response.setMessage("현재 세차장에서 처리할 수 없는 세차 기록입니다.");
                return response;
            }

            populateResponse(response, existingState);

            if (existingState.getDeviceId() == null
                    || (!CUSTOMER_MEMBER.equals(
                            existingState.getCustomerType()
                    ) && !CUSTOMER_NON_MEMBER.equals(
                            existingState.getCustomerType()
                    ))) {
                response.setAccepted(false);
                response.setEventType("WASH_DENIED");
                response.setMessage("실제 세차기 또는 고객 유형 정보가 없는 세차 기록입니다.");
                return response;
            }

            if (!existingState.getDeviceId().equals(request.getDeviceId())) {
                response.setAccepted(false);
                response.setEventType("WASH_REDIRECT");
                response.setAssignedDeviceId(
                        existingState.getDeviceId().longValue()
                );
                response.setMessage("처음 배정된 세차기로 이동해 주세요.");
                return response;
            }

            if (!WASH_ENTERED.equals(existingState.getWashStatus())) {
                response.setAccepted(false);
                response.setEventType("WASH_ALREADY_EXISTS");
                response.setMessage("이미 진행 중이거나 완료된 세차 기록이 있습니다.");
                return response;
            }

            WashCycleResponseDTO result = authorizeAndStartWash(
                    response,
                    existingState,
                    "WASH_STARTED"
            );

            completeReservationAfterSuccessfulStart(
                    existingState.getReservationId(),
                    result
            );
            return result;
        }
        LocalDateTime deviceCheckTime = LocalDateTime.now();
        LocalDateTime entryTime = entryExit.getEntryTime() == null
                ? deviceCheckTime
                : entryExit.getEntryTime();

        ReservationArrivalCheckDTO arrivalCheck =
                reservationService.checkArrivalReservation(
                        washSiteId.longValue(),
                        detection.getDetectedPlateNumber(),
                        entryTime,
                        deviceCheckTime
                );

        populateArrivalResponse(response, arrivalCheck);

        Long assignedDeviceId = arrivalCheck.getAssignedDeviceId();
        if (assignedDeviceId == null) {
            assignedDeviceId = reservationService.findAvailableDeviceForArrival(
                    washSiteId.longValue(),
                    arrivalCheck.getReservationId(),
                    arrivalCheck.getReservedDeviceId(),
                    deviceCheckTime
            );
            response.setAssignedDeviceId(assignedDeviceId);
        }

        if (assignedDeviceId == null) {
            response.setAccepted(false);
            response.setEventType("WASH_DENIED_NO_DEVICE");
            response.setMessage("현재 이용 가능한 세차기가 없습니다.");
            return response;
        }

        boolean hasReservation = arrivalCheck.getReservationId() != null;
        boolean guestReservation = hasReservation && "GUEST".equals(
                arrivalCheck.getReservationType()
        );
        if (guestReservation
                && !"PAID".equals(arrivalCheck.getPaymentStatus())) {
            response.setAccepted(false);
            response.setEventType("WASH_DENIED_PAYMENT_REQUIRED");
            response.setMessage("선결제가 완료되지 않은 비회원 예약입니다.");
            return response;
        }

        if (hasReservation
                && !guestReservation
                && (entryExit.getCustomerId() == null
                || entryExit.getVehicleId() == null)) {
            response.setAccepted(false);
            response.setEventType("WASH_DENIED_RESERVATION_REQUIRED");
            response.setMessage("회원 차량 정보를 확인할 수 없습니다.");
            return response;
        }

        if (!assignedDeviceId.equals(
                request.getDeviceId().longValue()
        )) {
            response.setAccepted(false);
            response.setDeviceId(request.getDeviceId());
            response.setEventType("WASH_REDIRECT");
            response.setMessage(
                    arrivalCheck.getMessage()
                            + " 안내 세차기 ID: "
                            + assignedDeviceId
            );
            return response;
        }

        WalkInCourseDTO walkInCourse = null;
        Integer courseId = arrivalCheck.getCourseId() == null
                ? null
                : Math.toIntExact(arrivalCheck.getCourseId());
        if (courseId == null) {
            walkInCourse = washCycleMapper.selectDefaultWalkInCourse(washSiteId);
            courseId = walkInCourse == null
                    ? null
                    : walkInCourse.getCourseId();
        }

        if (courseId == null) {
            response.setAccepted(false);
            response.setEventType("WASH_DENIED");
            response.setMessage(
                    "세차장에 사용 가능한 세차 코스가 없습니다."
            );
            return response;
        }

        boolean memberWalkIn = !hasReservation
                && entryExit.getCustomerId() != null
                && entryExit.getVehicleId() != null;
        String customerType = guestReservation
                || (!hasReservation && !memberWalkIn)
                ? CUSTOMER_NON_MEMBER
                : CUSTOMER_MEMBER;
        String paymentStatus = guestReservation
                ? PAYMENT_SUCCESS
                : PAYMENT_WAITING;
        Long reservationId = arrivalCheck.getReservationId();
        Integer washPrice = arrivalCheck.getReservationPrice() == null
                ? walkInCourse == null ? null : walkInCourse.getWashPrice()
                : arrivalCheck.getReservationPrice();

        Integer washHistoryId = washCycleMapper.createWashHistoryFromEntry(
                entryExit.getEntryExitId(),
                washSiteId,
                request.getDeviceId(),
                courseId,
                customerType,
                paymentStatus,
                reservationId,
                washPrice
        );
        if (washHistoryId == null) {
            response.setAccepted(false);
            response.setEventType("WASH_DENIED");
            response.setMessage("세차 이용기록을 생성할 수 없습니다.");
            return response;
        }

        plateMapper.updateWashHistoryId(
                detection.getPlateDetectionLogId(),
                washHistoryId
        );

        WashCycleStateDTO createdState =
                washCycleMapper.selectWashCycleState(washHistoryId);

        if (createdState == null) {
            response.setAccepted(false);
            response.setWashHistoryId(washHistoryId);
            response.setEventType("WASH_DENIED");
            response.setMessage("생성된 세차 기록을 확인할 수 없습니다.");
            return response;
        }

        populateResponse(response, createdState);

        WashCycleResponseDTO result = authorizeAndStartWash(
                response,
                createdState,
                "WASH_CREATED"
        );

        completeReservationAfterSuccessfulStart(
                reservationId,
                result
        );
        return result;
    }

    private void completeReservationAfterSuccessfulStart(
            Long reservationId,
            WashCycleResponseDTO response
    ) {
        if (reservationId != null
                && Boolean.TRUE.equals(response.getAccepted())) {
            reservationService.completeReservation(reservationId);
            response.setReservationStatus("FULFILLED");
        }
    }

    private WashCycleResponseDTO authorizeAndStartWash(
            WashCycleResponseDTO response,
            WashCycleStateDTO state,
            String successEventType
    ) {
        if (CUSTOMER_MEMBER.equals(state.getCustomerType())
                && !isPaymentSuccess(state.getPaymentStatus())) {
            PrepaidUseResponseDTO prepaidResult =
                    prepaidService.autoUseWithBillingTopUp(
                            state.getCustomerId().longValue(),
                            state.getWashHistoryId().longValue()
                    );

            if (!isPrepaidUseSuccess(prepaidResult)) {
                response.setAccepted(false);
                response.setAutoPaymentSuccess(false);
                response.setEventType("WASH_DENIED");
                response.setWashStatus("PAYMENT_REQUIRED");
                response.setPaymentStatus(PAYMENT_WAITING);
                response.setMessage("잔액이 부족하거나 자동결제를 사용할 수 없어 세차를 시작할 수 없습니다.");
                return response;
            }

            state.setPaymentStatus(PAYMENT_SUCCESS);
            response.setAutoPaymentSuccess(true);
            response.setPaidAmount(prepaidResult.getAmount());
            response.setWalletBalanceAfter(prepaidResult.getBalance());
        } else if (CUSTOMER_MEMBER.equals(state.getCustomerType())) {
            response.setAutoPaymentSuccess(true);
        } else if (CUSTOMER_NON_MEMBER.equals(state.getCustomerType())
                && PAYMENT_WAITING.equals(state.getPaymentStatus())) {
            response.setAutoPaymentSuccess(false);
        } else if (CUSTOMER_NON_MEMBER.equals(state.getCustomerType())) {
            if (!isPaymentSuccess(state.getPaymentStatus())) {
                response.setAccepted(false);
                response.setAutoPaymentSuccess(false);
                response.setEventType("WASH_DENIED");
                response.setMessage("결제 상태를 확인할 수 없어 세차를 시작하지 않았습니다.");
                return response;
            }
            response.setAutoPaymentSuccess(true);
        }

        int started = washCycleMapper.startWashIfAllowed(
                state.getWashHistoryId()
        );

        if (started == 0) {
            response.setAccepted(false);
            response.setEventType("WASH_DENIED");
            response.setMessage("결제 상태 또는 세차 진행 상태를 확인할 수 없어 세차를 시작하지 않았습니다.");
            return response;
        }

        response.setAccepted(true);
        response.setEventType(successEventType);
        response.setWashStatus(WASHING);
        response.setPaymentStatus(state.getPaymentStatus());
        response.setMessage(
                CUSTOMER_NON_MEMBER.equals(state.getCustomerType())
                        && PAYMENT_WAITING.equals(state.getPaymentStatus())
                        ? "세차를 시작합니다. 출차 전에 결제를 완료해 주세요."
                        : "세차 시작이 승인되었습니다."
        );
        return response;
    }

    private void populateResponse(
            WashCycleResponseDTO response,
            WashCycleStateDTO state
    ) {
        response.setWashHistoryId(state.getWashHistoryId());
        response.setCustomerId(state.getCustomerId());
        response.setVehicleId(state.getVehicleId());
        response.setCustomerType(state.getCustomerType());
        response.setWashSiteId(state.getWashSiteId());
        response.setDeviceId(state.getDeviceId());
        response.setCourseId(state.getCourseId());
        response.setReservationId(state.getReservationId());
        response.setReservationMatched(state.getReservationId() != null);
        response.setReservationStatus(state.getReservationStatus());
        response.setReservedDeviceId(state.getReservedDeviceId());
        response.setAssignedDeviceId(
                state.getDeviceId() == null
                        ? null
                        : state.getDeviceId().longValue()
        );
        response.setPaymentStatus(state.getPaymentStatus());
        response.setWashStatus(state.getWashStatus());
    }

    private void populateArrivalResponse(
            WashCycleResponseDTO response,
            ReservationArrivalCheckDTO arrivalCheck
    ) {
        response.setReservationMatched(
                arrivalCheck.getReservationId() != null
        );
        response.setReservationId(arrivalCheck.getReservationId());
        response.setReservationStatus(arrivalCheck.getStatus().name());
        response.setReservedDeviceId(arrivalCheck.getReservedDeviceId());
        response.setAssignedDeviceId(arrivalCheck.getAssignedDeviceId());
    }

    private boolean isPaymentSuccess(String paymentStatus) {
        return PAYMENT_SUCCESS.equals(paymentStatus)
                || PAYMENT_DONE.equals(paymentStatus);
    }

    private boolean isPrepaidUseSuccess(
            PrepaidUseResponseDTO prepaidResult
    ) {
        return prepaidResult != null
                && PAYMENT_SUCCESS.equals(prepaidResult.getStatus());
    }

    private boolean isDetectionFailed(PlateDetectionResponseDTO detection) {
        return !Boolean.TRUE.equals(detection.getSuccess())
                || detection.getDetectedPlateNumber() == null
                || detection.getDetectedPlateNumber().isBlank();
    }

    private void requireWashCamera(Integer deviceId) {
        String deviceType =
                gateMapper.selectDeviceTypeById(deviceId);

        if (!"WASH_CAMERA".equals(deviceType)
                && !"WASH".equals(deviceType)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "세차 카메라만 세차를 시작할 수 있습니다."
            );
        }
    }
}
