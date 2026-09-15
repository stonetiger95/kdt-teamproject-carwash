package kdt.washPrj.nonMemberReservation;

import kdt.washPrj.global.vehicle.PlateNumberNormalizer;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationAccessDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationAuthRecordDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationCancelDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationCreateDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationResponseDTO;
import kdt.washPrj.reservation.ReservationTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class NonMemberReservationService {

    private static final Logger log =
            LoggerFactory.getLogger(NonMemberReservationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final NonMemberReservationMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final ReservationTimeProvider timeProvider;

    public NonMemberReservationService(
            NonMemberReservationMapper mapper,
            PasswordEncoder passwordEncoder,
            ReservationTimeProvider timeProvider
    ) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.timeProvider = timeProvider;
    }

    @Transactional
    public NonMemberReservationResponseDTO createPendingReservation(
            NonMemberReservationCreateDTO dto
    ) {
        validateCreateRequest(dto);

        String plateNumber = normalizePlateNumber(dto.getPlateNumber());
        String phone = normalizePhone(dto.getPhone());
        LocalDateTime endTime = dto.getStartTime().plusMinutes(
                NonMemberReservationPolicy.DURATION_MINUTES
        );

        if (mapper.existsPlateTimeConflict(
                plateNumber,
                dto.getStartTime(),
                endTime
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "해당 차량번호로 같은 시간대의 예약이 존재합니다."
            );
        }

        String internalAccessSecret = generateInternalAccessSecret();
        Long reservationId;

        try {
            reservationId = mapper.insertPendingReservation(
                    dto,
                    plateNumber,
                    phone,
                    passwordEncoder.encode(internalAccessSecret)
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
                    "세차장, 코스, 예약 가능 대수 또는 영업시간을 확인해 주세요."
            );
        }

        insertHistory(
                reservationId,
                "CREATED_BY_GUEST",
                null,
                "PENDING_PAYMENT",
                "GUEST",
                null
        );

        NonMemberReservationResponseDTO response =
                requireReservation(reservationId);
        response.setPaymentRequired(true);

        // TODO(payment): reservationId를 결제 주문에 연결하고 PayApp 결제 URL을 생성한다.
        // 결제 성공 콜백에서는 completePayment(reservationId)를 호출한다.
        // 결제 실패 콜백에서는 failPayment(reservationId, reason)를 호출한다.
        return response;
    }

    public NonMemberReservationResponseDTO getReservation(
            NonMemberReservationAccessDTO dto
    ) {
        requireAccess(dto);
        NonMemberReservationResponseDTO response =
                requireReservation(dto.getReservationId());
        response.setPaymentRequired(
                "PENDING_PAYMENT".equals(response.getStatus().name())
        );
        return response;
    }

    @Transactional
    public void cancelReservation(
            Long reservationId,
            NonMemberReservationCancelDTO dto
    ) {
        validateCancelRequest(reservationId, dto);
        NonMemberReservationAuthRecordDTO record = requireAccess(
                reservationId,
                dto.getPhone()
        );

        if ("CONFIRMED".equals(record.getStatus())
                && "PAID".equals(record.getPaymentStatus())) {
            // TODO(payment): 결제 패키지에서 환불 성공 후 예약을 CANCELED/REFUNDED로 변경한다.
            throw new ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "결제 완료 예약의 환불 연결이 아직 필요합니다."
            );
        }

        int updated = mapper.cancelPendingReservation(
                reservationId,
                dto.getCancelReason().trim()
        );

        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "현재 상태에서는 예약을 취소할 수 없습니다."
            );
        }

        insertHistory(
                reservationId,
                "CANCELED_BY_GUEST",
                "PENDING_PAYMENT",
                "CANCELED",
                "GUEST",
                dto.getCancelReason().trim()
        );
    }

    /**
     * 결제 성공 콜백 연결 지점. Payment 패키지에서 호출한다.
     */
    @Transactional
    public void completePayment(Long reservationId) {
        int updated = mapper.confirmPayment(reservationId);

        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "결제 완료로 변경할 수 없는 예약입니다."
            );
        }

        insertHistory(
                reservationId,
                "PAYMENT_COMPLETED",
                "PENDING_PAYMENT",
                "CONFIRMED",
                "SYSTEM",
                null
        );
    }

    /**
     * 결제 실패 콜백 연결 지점. Payment 패키지에서 호출한다.
     */
    @Transactional
    public void failPayment(Long reservationId, String reason) {
        String failureReason = reason == null || reason.isBlank()
                ? "Payment failed"
                : reason.trim();
        int updated = mapper.failPayment(reservationId, failureReason);

        if (updated == 1) {
            insertHistory(
                    reservationId,
                    "PAYMENT_FAILED",
                    "PENDING_PAYMENT",
                    "CANCELED",
                    "SYSTEM",
                    failureReason
            );
        }
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    @Transactional
    public void expirePendingReservations() {
        int expired = mapper.expireGuestReservationPayments();
        if (expired > 0) {
            log.info("Expired {} guest reservation payment holds.", expired);
        }
    }

    private void validateCreateRequest(NonMemberReservationCreateDTO dto) {
        if (dto == null
                || dto.getWashSiteId() == null || dto.getWashSiteId() <= 0
                || dto.getCourseId() == null || dto.getCourseId() <= 0
                || dto.getStartTime() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "비회원 예약 요청값을 확인해 주세요."
            );
        }

        normalizePlateNumber(dto.getPlateNumber());
        normalizePhone(dto.getPhone());

        LocalDateTime now = timeProvider.now();
        LocalDateTime startTime = dto.getStartTime();

        if (startTime.isBefore(
                now.plusHours(NonMemberReservationPolicy.MIN_ADVANCE_HOURS)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약은 시작 1시간 전까지만 가능합니다."
            );
        }

        if (startTime.isAfter(
                now.plusDays(NonMemberReservationPolicy.MAX_ADVANCE_DAYS)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약은 7일 이내만 가능합니다."
            );
        }

        if ((startTime.getMinute() != 0 && startTime.getMinute() != 30)
                || startTime.getSecond() != 0
                || startTime.getNano() != 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약은 30분 단위로 선택해 주세요."
            );
        }
    }

    private void validateCancelRequest(
            Long reservationId,
            NonMemberReservationCancelDTO dto
    ) {
        if (reservationId == null || reservationId <= 0
                || dto == null
                || dto.getCancelReason() == null
                || dto.getCancelReason().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 ID와 취소 요청값을 확인해 주세요."
            );
        }

        if (dto.getCancelReason().trim().length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "취소 사유는 500자 이하여야 합니다."
            );
        }
    }

    private NonMemberReservationAuthRecordDTO requireAccess(
            NonMemberReservationAccessDTO dto
    ) {
        if (dto == null || dto.getReservationId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "비회원 예약 인증정보가 필요합니다."
            );
        }
        return requireAccess(
                dto.getReservationId(),
                dto.getPhone()
        );
    }

    private NonMemberReservationAuthRecordDTO requireAccess(
            Long reservationId,
            String phone
    ) {
        String normalizedPhone = normalizePhone(phone);

        NonMemberReservationAuthRecordDTO record =
                mapper.selectAuthRecord(reservationId);

        if (record == null
                || !normalizedPhone.equals(record.getGuestPhone())) {
            throw unauthorized();
        }
        return record;
    }

    private NonMemberReservationResponseDTO requireReservation(
            Long reservationId
    ) {
        NonMemberReservationResponseDTO response =
                mapper.selectReservation(reservationId);
        if (response == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "비회원 예약을 찾을 수 없습니다."
            );
        }
        return response;
    }

    private String normalizePlateNumber(String plateNumber) {
        String normalized = PlateNumberNormalizer.normalize(plateNumber);
        if (normalized == null
                || !normalized.matches("^[0-9가-힣]{3,8}\\d{4}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 전체 차량번호를 입력해 주세요."
            );
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        String normalized = phone == null
                ? ""
                : phone.replaceAll("[^0-9]", "");
        if (!normalized.matches("^0\\d{9,10}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 연락처를 입력해 주세요."
            );
        }
        return normalized;
    }

    private String generateInternalAccessSecret() {
        return Long.toUnsignedString(SECURE_RANDOM.nextLong(), 36)
                + Long.toUnsignedString(SECURE_RANDOM.nextLong(), 36);
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "예약번호와 예약 당시 연락처가 일치하지 않습니다."
        );
    }

    private void insertHistory(
            Long reservationId,
            String eventType,
            String fromStatus,
            String toStatus,
            String actorType,
            String reason
    ) {
        int inserted = mapper.insertHistory(
                reservationId,
                eventType,
                fromStatus,
                toStatus,
                actorType,
                reason
        );
        if (inserted != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "비회원 예약 이력을 저장하지 못했습니다."
            );
        }
    }
}
