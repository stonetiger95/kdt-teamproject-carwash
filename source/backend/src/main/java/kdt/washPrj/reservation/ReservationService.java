package kdt.washPrj.reservation;

import kdt.washPrj.global.vehicle.PlateNumberNormalizer;
import kdt.washPrj.reservation.dto.ReservationCancelDTO;
import kdt.washPrj.reservation.dto.ReservationCreateDTO;
import kdt.washPrj.reservation.dto.ReservationResponseDTO;
import kdt.washPrj.reservation.dto.ReservationArrivalCheckDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class ReservationService {

    private static final Logger log =
            LoggerFactory.getLogger(ReservationService.class);

    private final ReservationMapper reservationMapper;
    private final ReservationTimeProvider timeProvider;

    public ReservationService(
            ReservationMapper reservationMapper,
            ReservationTimeProvider timeProvider
    ) {
        this.reservationMapper = reservationMapper;
        this.timeProvider = timeProvider;
    }

    @Transactional
    public Long createReservation(
            String loginId,
            ReservationCreateDTO dto
    ) {
        requireLoginId(loginId);
        validateCreateRequest(dto);

        Long reservationId;

        try {
            reservationId = reservationMapper.insertReservation(
                    loginId,
                    dto
            );
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "해당 시간에 다른 예약이 먼저 등록되었습니다.",
                    exception
            );
        }

        if (reservationId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "차량, 세차장, 코스, 예약 가능 대수 또는 영업시간을 확인해 주세요."
            );
        }

        return reservationId;
    }

    @Transactional
    public void cancelCustomerReservation(
            String loginId,
            Long reservationId,
            ReservationCancelDTO dto
    ) {
        requireLoginId(loginId);
        requireReservationId(reservationId);
        validateCancelRequest(dto);

        int updatedCount =
                reservationMapper.cancelCustomerReservation(
                        loginId,
                        reservationId,
                        dto
                );

        if (updatedCount == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "예약 상태와 취소 가능 시간을 확인해 주세요."
            );
        }

    }

    @Transactional
    public void cancelOwnerReservation(
            String loginId,
            Long reservationId,
            ReservationCancelDTO dto
    ) {
        requireLoginId(loginId);
        requireReservationId(reservationId);
        validateCancelRequest(dto);

        int updatedCount =
                reservationMapper.cancelOwnerReservation(
                        loginId,
                        reservationId,
                        dto
                );

        if (updatedCount == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "예약 상태와 취소 가능 시간을 확인해 주세요."
            );
        }

    }

    @Transactional
    public ReservationResponseDTO findArrivalReservationForWash(
            Long washSiteId,
            String plateNumber,
            LocalDateTime entryTime
    ) {
        if (washSiteId == null || washSiteId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "세차장 ID가 필요합니다."
            );
        }

        String normalizedPlateNumber =
                PlateNumberNormalizer.normalize(plateNumber);

        if (normalizedPlateNumber == null
                || normalizedPlateNumber.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "차량번호가 필요합니다."
            );
        }

        if (entryTime == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "입차 시각이 필요합니다."
            );
        }

        ReservationResponseDTO reservation =
                reservationMapper.selectArrivalReservationForUpdate(
                        washSiteId,
                        normalizedPlateNumber,
                        entryTime
                );

        // 예약이 없는 현장 방문 차량은 기존 세차 흐름을 계속 사용한다.
        if (reservation == null) {
            return null;
        }

        return reservation;
    }

    /*
      입차 시점의 예약 상태를 판정한다.
      장치 가용성은 다음 단계에서 assignedDeviceId를 재선정한다.
    */
    @Transactional
    public ReservationArrivalCheckDTO checkArrivalReservation(
            Long washSiteId,
            String plateNumber,
            LocalDateTime arrivalTime
    ) {
        return checkArrivalReservation(
                washSiteId,
                plateNumber,
                arrivalTime,
                arrivalTime
        );
    }

    @Transactional
    public ReservationArrivalCheckDTO checkArrivalReservation(
            Long washSiteId,
            String plateNumber,
            LocalDateTime arrivalTime,
            LocalDateTime deviceCheckTime
    ) {
        validateArrivalRequest(washSiteId, plateNumber, arrivalTime);

        if (deviceCheckTime == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "세차기 확인 시간이 필요합니다."
            );
        }

        String normalizedPlateNumber =
                PlateNumberNormalizer.normalize(plateNumber);

        ReservationResponseDTO reservation =
                reservationMapper.selectReservationForArrivalCheck(
                        washSiteId,
                        normalizedPlateNumber,
                        arrivalTime
                );

        if (reservation == null) {
            return ReservationArrivalCheckDTO.notFound(
                    "예약이 없어 입차할 수 없습니다."
            );
        }

        ReservationArrivalCheckDTO result =
                new ReservationArrivalCheckDTO();
        result.setReservationId(reservation.getReservationId());
        result.setReservationType(reservation.getReservationType());
        result.setPaymentStatus(reservation.getPaymentStatus());
        result.setReservedDeviceId(reservation.getDeviceId());
        result.setCourseId(reservation.getCourseId());
        result.setReservationPrice(reservation.getReservationPrice());
        result.setReservationStartTime(reservation.getStartTime());
        result.setReservationEndTime(reservation.getEndTime());

        ReservationArrivalStatus arrivalStatus =
                ReservationArrivalStatus.MATCHED;
        if (arrivalTime.isAfter(
                reservation.getStartTime()
                        .plusMinutes(ReservationPolicy.ARRIVAL_GRACE_MINUTES)
        )) {
            arrivalStatus = ReservationArrivalStatus.TOO_LATE;
            reservationMapper.recordArrivalDeviation(
                    reservation.getReservationId(),
                    "ARRIVED_LATE",
                    arrivalTime
            );
        }

        Long assignedDeviceId =
                reservationMapper.selectAvailableDeviceForArrival(
                        washSiteId,
                        reservation.getReservationId(),
                        reservation.getDeviceId(),
                        deviceCheckTime
                );

        if (assignedDeviceId == null || assignedDeviceId <= 0) {
            result.setStatus(ReservationArrivalStatus.NO_DEVICE);
            result.setMessage(
                    "현재 이용 가능한 세차기가 없습니다. 잠시 후 다시 시도해 주세요."
            );
            return result;
        }

        result.setAssignedDeviceId(assignedDeviceId);
        result.setStatus(arrivalStatus);
        result.setMessage(
                assignedDeviceId.equals(reservation.getDeviceId())
                        ? "예약 차량입니다. 예약한 세차기로 이동해 주세요."
                        : "예약 세차기가 사용 중입니다. 안내된 세차기로 이동해 주세요."
        );
        return result;
    }

    @Transactional
    public Long findAvailableDeviceForArrival(
            Long washSiteId,
            Long reservationId,
            Long reservedDeviceId,
            LocalDateTime arrivalTime
    ) {
        if (washSiteId == null || washSiteId <= 0 || arrivalTime == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "세차장과 입차 시간을 확인해 주세요."
            );
        }

        return reservationMapper.selectAvailableDeviceForArrival(
                washSiteId,
                reservationId,
                reservedDeviceId,
                arrivalTime
        );
    }

    private void validateArrivalRequest(
            Long washSiteId,
            String plateNumber,
            LocalDateTime arrivalTime
    ) {
        if (washSiteId == null || washSiteId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "세차장 ID가 필요합니다."
            );
        }

        if (plateNumber == null || plateNumber.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "차량번호가 필요합니다."
            );
        }

        if (arrivalTime == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "입차 시간이 필요합니다."
            );
        }
    }

    @Transactional
    public void completeReservation(Long reservationId) {
        requireReservationId(reservationId);

        int updatedCount =
                reservationMapper.fulfillReservation(reservationId);

        if (updatedCount == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 사용되었거나 처리할 수 없는 예약입니다."
            );
        }

    }

    @Scheduled(
            fixedDelay = 300_000L,
            initialDelay = 60_000L
    )
    @Transactional
    public void markExpiredReservationsAsNoShow() {
        LocalDateTime noShowCutoff = timeProvider.now().minusMinutes(
                ReservationPolicy.ARRIVAL_GRACE_MINUTES
        );
        int updatedCount =
                reservationMapper.updateExpiredReservationsToNoShow(
                        noShowCutoff
                );

        if (updatedCount > 0) {
            log.info(
                    "Marked {} expired reservations as NO_SHOW.",
                    updatedCount
            );
        }
    }

    private void validateCreateRequest(
            ReservationCreateDTO dto
    ) {
        if (dto == null
                || dto.getVehicleId() == null
                || dto.getVehicleId() <= 0
                || dto.getWashSiteId() == null
                || dto.getWashSiteId() <= 0
                || dto.getCourseId() == null
                || dto.getCourseId() <= 0
                || dto.getStartTime() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 요청값을 확인해 주세요."
            );
        }

        LocalDateTime startTime = dto.getStartTime();
        LocalDateTime now = timeProvider.now();

        if (startTime.isBefore(
                now.plusHours(ReservationPolicy.MIN_ADVANCE_HOURS)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약은 시작 1시간 전까지만 가능합니다."
            );
        }

        if (startTime.isAfter(
                now.plusDays(ReservationPolicy.MAX_ADVANCE_DAYS)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약은 7일 이내만 가능합니다."
            );
        }

        if (startTime.getMinute() != 0
                && startTime.getMinute() != 30) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약은 30분 단위로 선택해 주세요."
            );
        }

        if (startTime.getSecond() != 0
                || startTime.getNano() != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 시간의 초 단위는 사용할 수 없습니다."
            );
        }
    }

    private void validateCancelRequest(
            ReservationCancelDTO dto
    ) {
        if (dto == null
                || dto.getCancelReason() == null
                || dto.getCancelReason().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "취소 사유를 입력해 주세요."
            );
        }

        String cancelReason = dto.getCancelReason().trim();

        if (cancelReason.length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "취소 사유는 500자 이하여야 합니다."
            );
        }

        dto.setCancelReason(cancelReason);
    }

    private void requireLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }
    }

    private void requireReservationId(Long reservationId) {
        if (reservationId == null || reservationId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 ID를 확인해 주세요."
            );
        }
    }

}
