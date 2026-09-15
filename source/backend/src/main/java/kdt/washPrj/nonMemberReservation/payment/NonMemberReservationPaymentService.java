package kdt.washPrj.nonMemberReservation.payment;

import kdt.washPrj.nonMemberReservation.NonMemberReservationService;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationAccessDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.NonMemberReservationPaymentRecordDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.NonMemberReservationPaymentRequestDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.NonMemberReservationPaymentResponseDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.NonMemberReservationPaymentTargetDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.GuestReservationRefundPreparationDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.GuestReservationRefundResponseDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationResponseDTO;
import kdt.washPrj.payment.PayAppClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NonMemberReservationPaymentService {

    private static final Logger log = LoggerFactory.getLogger(
            NonMemberReservationPaymentService.class
    );
    private static final String PAYMENT_WAITING = "WAITING";
    private static final String PAYMENT_SUCCESS = "SUCCESS";
    private static final String PAYMENT_CANCELED = "CANCELED";
    private static final String CONTEXT_PREFIX = "GUEST_RESERVATION:";

    private final NonMemberReservationPaymentMapper mapper;
    private final NonMemberReservationService reservationService;
    private final PayAppClient payAppClient;
    private final Set<Long> activeRequests = ConcurrentHashMap.newKeySet();
    private final Set<Long> activeRefunds = ConcurrentHashMap.newKeySet();

    @Value("${payapp.userid:}")
    private String payAppUserid;

    @Value("${payapp.link-key:}")
    private String payAppLinkKey;

    @Value("${payapp.link-value:}")
    private String payAppLinkValue;

    @Value("${payapp.open-pay-type:card}")
    private String openPayType;

    @Value("${app.backend-base-url}")
    private String backendBaseUrl;

    public NonMemberReservationPaymentService(
            NonMemberReservationPaymentMapper mapper,
            NonMemberReservationService reservationService,
            PayAppClient payAppClient
    ) {
        this.mapper = mapper;
        this.reservationService = reservationService;
        this.payAppClient = payAppClient;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public NonMemberReservationPaymentResponseDTO requestPayment(
            NonMemberReservationPaymentRequestDTO requestDTO
    ) {
        validateRequest(requestDTO);
        authenticateReservation(requestDTO);

        Long reservationId = requestDTO.getReservationId();
        if (!activeRequests.add(reservationId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "해당 예약의 결제 요청이 이미 진행 중입니다."
            );
        }

        try {
            return createPaymentRequest(requestDTO);
        } finally {
            activeRequests.remove(reservationId);
        }
    }

    @Transactional
    public String handlePayAppFeedback(Map<String, String> params) {
        if (!isValidFeedback(params)) {
            log.warn("Guest reservation PayApp feedback validation failed.");
            return "FAIL";
        }

        Long paymentHistoryId = parseLong(params.get("var1"));
        Long reservationId = parseReservationId(params.get("var2"));
        Integer amount = parseInteger(params.get("price"));
        String paymentStatus = toPaymentStatus(params.get("pay_state"));

        if (paymentHistoryId == null
                || reservationId == null
                || amount == null
                || paymentStatus == null) {
            log.warn(
                    "Guest reservation feedback required value missing. paymentId={}, reservationId={}, amount={}, payState={}",
                    paymentHistoryId,
                    reservationId,
                    amount,
                    params.get("pay_state")
            );
            return "FAIL";
        }

        NonMemberReservationPaymentRecordDTO payment =
                mapper.selectPaymentForUpdate(
                        paymentHistoryId,
                        reservationId
                );

        if (payment == null || !amount.equals(payment.getAmount())) {
            log.warn(
                    "Guest reservation payment lookup or amount check failed. paymentId={}, reservationId={}",
                    paymentHistoryId,
                    reservationId
            );
            return "FAIL";
        }

        String payAppPaymentId = firstNonBlank(
                params.get("mul_no"),
                params.get("payapp_transaction_id")
        );

        if (PAYMENT_CANCELED.equals(paymentStatus)
                && (PAYMENT_SUCCESS.equals(payment.getPaymentStatus())
                || (PAYMENT_CANCELED.equals(payment.getPaymentStatus())
                && "REFUNDED".equals(
                        payment.getReservationPaymentStatus()
                )))) {
            return handleRefundFeedback(
                    paymentHistoryId,
                    reservationId,
                    amount,
                    payAppPaymentId,
                    params.get("pay_state"),
                    payment.getReservationStatus()
            );
        }

        if (paymentStatus.equals(payment.getPaymentStatus())) {
            return "SUCCESS";
        }

        if (PAYMENT_WAITING.equals(paymentStatus)) {
            return "SUCCESS";
        }

        if (!PAYMENT_WAITING.equals(payment.getPaymentStatus())) {
            log.warn(
                    "Guest reservation payment state transition rejected. paymentId={}, from={}, to={}",
                    paymentHistoryId,
                    payment.getPaymentStatus(),
                    paymentStatus
            );
            return "FAIL";
        }

        LocalDateTime processedAt = parsePayDate(
                PAYMENT_CANCELED.equals(paymentStatus)
                        ? firstNonBlank(
                                params.get("canceldate"),
                                params.get("pay_date")
                        )
                        : params.get("pay_date")
        );
        String paymentType = toPaymentType(params.get("pay_type"));
        String cancelReason = firstNonBlank(
                params.get("cancelmemo"),
                params.get("cancel_reason")
        );

        int updated = mapper.updateFromFeedback(
                paymentHistoryId,
                reservationId,
                paymentType,
                paymentStatus,
                processedAt,
                payAppPaymentId,
                cancelReason
        );
        if (updated != 1) {
            return "FAIL";
        }

        if (PAYMENT_SUCCESS.equals(paymentStatus)) {
            reservationService.completePayment(reservationId);
        } else {
            reservationService.failPayment(
                    reservationId,
                    cancelReason == null
                            ? "PayApp payment canceled"
                            : cancelReason
            );
        }

        return "SUCCESS";
    }

    public GuestReservationRefundResponseDTO refundPaidReservation(
            Long reservationId,
            String phone,
            String cancelReason
    ) {
        String normalizedReason = validateRefundRequest(
                reservationId,
                phone,
                cancelReason
        );
        authenticateRefundReservation(reservationId, phone);

        if (!activeRefunds.add(reservationId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "해당 예약의 환불 요청이 이미 진행 중입니다."
            );
        }

        try {
            GuestReservationRefundPreparationDTO preparation =
                    mapper.prepareGuestReservationRefund(
                            reservationId,
                            normalizedReason
                    );
            requirePreparedRefund(preparation);

            Map<String, String> payAppResult;
            try {
                payAppResult = requestFullPayAppRefund(
                        preparation,
                        normalizedReason
                );
            } catch (RuntimeException exception) {
                failPreparedRefund(
                        preparation.getRefundHistoryId(),
                        limitProviderResponse(exception.getMessage())
                );
                throw exception;
            }

            String providerResponse = limitProviderResponse(
                    String.valueOf(payAppResult)
            );
            if (payAppResult == null
                    || !"1".equals(payAppResult.get("state"))) {
                failPreparedRefund(
                        preparation.getRefundHistoryId(),
                        providerResponse
                );
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        limitReason(payAppResult == null
                                ? "PayApp 환불 응답이 없습니다."
                                : payAppResult.getOrDefault(
                                        "errorMessage",
                                        "PayApp 환불 요청에 실패했습니다."
                                ))
                );
            }

            try {
                int completed = mapper.completeGuestReservationRefund(
                        preparation.getRefundHistoryId(),
                        providerResponse
                );
                if (completed == 1) {
                    return toRefundResponse(preparation);
                }
                markRefundReconciliationRequired(
                        preparation.getRefundHistoryId(),
                        providerResponse + ";localSync=PENDING"
                );
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "결제 취소는 승인되었지만 환불 상태를 완료하지 못했습니다."
                );
            } catch (ResponseStatusException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                markRefundReconciliationRequired(
                        preparation.getRefundHistoryId(),
                        providerResponse + ";localSync=PENDING"
                );
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "결제 취소는 승인되었지만 환불 상태 재조정이 필요합니다.",
                        exception
                );
            }
        } finally {
            activeRefunds.remove(reservationId);
        }
    }

    @Scheduled(
            fixedDelayString = "${guest.no-show-refund.fixed-delay-ms:300000}",
            initialDelayString = "${guest.no-show-refund.initial-delay-ms:90000}"
    )
    public void refundPaidNoShowReservations() {
        List<Long> reservationIds;
        try {
            reservationIds = mapper.selectGuestNoShowRefundCandidates(20);
        } catch (RuntimeException exception) {
            log.error("Failed to load guest NO_SHOW refund candidates.", exception);
            return;
        }

        if (reservationIds == null || reservationIds.isEmpty()) {
            return;
        }
        for (Long reservationId : reservationIds) {
            try {
                refundPaidNoShowReservation(reservationId);
            } catch (RuntimeException exception) {
                log.error(
                        "Guest NO_SHOW refund processing failed. reservationId={}",
                        reservationId,
                        exception
                );
            }
        }
    }

    private void refundPaidNoShowReservation(Long reservationId) {
        if (reservationId == null || !activeRefunds.add(reservationId)) {
            return;
        }

        final String reason = "비회원 예약 NO_SHOW 자동 환불";
        try {
            GuestReservationRefundPreparationDTO preparation =
                    mapper.prepareGuestNoShowRefund(reservationId, reason);
            if (preparation == null
                    || !"OK".equals(preparation.getResultCode())) {
                log.info(
                        "Guest NO_SHOW refund skipped. reservationId={}, result={}",
                        reservationId,
                        preparation == null ? null : preparation.getResultCode()
                );
                return;
            }

            Map<String, String> payAppResult;
            try {
                payAppResult = requestFullPayAppRefund(preparation, reason);
            } catch (RuntimeException exception) {
                failPreparedRefund(
                        preparation.getRefundHistoryId(),
                        limitProviderResponse(exception.getMessage())
                );
                log.warn(
                        "Guest NO_SHOW PayApp refund request failed. reservationId={}",
                        reservationId,
                        exception
                );
                return;
            }

            String providerResponse = limitProviderResponse(
                    String.valueOf(payAppResult)
            );
            if (payAppResult == null
                    || !"1".equals(payAppResult.get("state"))) {
                failPreparedRefund(
                        preparation.getRefundHistoryId(),
                        providerResponse
                );
                log.warn(
                        "Guest NO_SHOW PayApp refund rejected. reservationId={}, response={}",
                        reservationId,
                        providerResponse
                );
                return;
            }

            try {
                int completed = mapper.completeGuestNoShowRefund(
                        preparation.getRefundHistoryId(),
                        providerResponse
                );
                if (completed != 1) {
                    markRefundReconciliationRequired(
                            preparation.getRefundHistoryId(),
                            providerResponse + ";localSync=PENDING"
                    );
                }
            } catch (RuntimeException exception) {
                markRefundReconciliationRequired(
                        preparation.getRefundHistoryId(),
                        providerResponse + ";localSync=PENDING"
                );
                log.error(
                        "Guest NO_SHOW refund requires reconciliation. reservationId={}",
                        reservationId,
                        exception
                );
            }
        } finally {
            activeRefunds.remove(reservationId);
        }
    }

    private String handleRefundFeedback(
            Long paymentHistoryId,
            Long reservationId,
            Integer amount,
            String payAppPaymentId,
            String payState,
            String reservationStatus
    ) {
        if (isBlank(payAppPaymentId)) {
            log.warn(
                    "Guest refund feedback transaction id missing. paymentId={}, reservationId={}",
                    paymentHistoryId,
                    reservationId
            );
            return "FAIL";
        }

        String providerResponse = limitProviderResponse(
                "state=" + payState
                        + ";transaction=" + payAppPaymentId
                        + ";amount=" + amount
        );
        String result = "NO_SHOW".equals(reservationStatus)
                ? mapper.applyGuestNoShowRefundFeedback(
                        paymentHistoryId,
                        reservationId,
                        amount,
                        payAppPaymentId,
                        providerResponse
                )
                : mapper.applyGuestReservationRefundFeedback(
                        paymentHistoryId,
                        reservationId,
                        amount,
                        payAppPaymentId,
                        providerResponse
                );
        if ("APPLIED".equals(result)
                || "ALREADY_APPLIED".equals(result)) {
            return "SUCCESS";
        }

        log.warn(
                "Guest refund feedback rejected. paymentId={}, reservationId={}, result={}",
                paymentHistoryId,
                reservationId,
                result
        );
        return "FAIL";
    }

    private NonMemberReservationPaymentResponseDTO createPaymentRequest(
            NonMemberReservationPaymentRequestDTO requestDTO
    ) {
        Long reservationId = requestDTO.getReservationId();
        NonMemberReservationPaymentTargetDTO target =
                mapper.selectPaymentTargetForUpdate(reservationId);

        if (target == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "결제 가능한 비회원 예약이 아닙니다."
            );
        }

        NonMemberReservationPaymentRecordDTO activePayment =
                mapper.selectActivePaymentForUpdate(reservationId);
        if (activePayment != null) {
            String message = PAYMENT_SUCCESS.equals(
                    activePayment.getPaymentStatus()
            )
                    ? "이미 결제가 완료된 예약입니다."
                    : "이미 진행 중인 결제 요청이 있습니다.";
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    message
            );
        }

        Long paymentHistoryId = mapper.reservePaymentHistoryId();
        if (paymentHistoryId == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "결제번호를 생성하지 못했습니다."
            );
        }

        int inserted = mapper.insertWaitingPayment(
                paymentHistoryId,
                reservationId,
                target.getAmount()
        );
        if (inserted != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 진행 중인 결제가 있습니다."
            );
        }

        Map<String, String> payAppResult;
        try {
            payAppResult = requestPayApp(paymentHistoryId, target);
        } catch (RuntimeException exception) {
            mapper.failPaymentRequest(
                    paymentHistoryId,
                    reservationId,
                    "PayApp 결제요청 통신 오류"
            );
            throw exception;
        }

        if (!"1".equals(payAppResult.get("state"))) {
            String reason = limitReason(payAppResult.getOrDefault(
                    "errorMessage",
                    "PayApp 비회원 예약 결제요청 실패"
            ));
            mapper.failPaymentRequest(
                    paymentHistoryId,
                    reservationId,
                    reason
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    reason
            );
        }

        return toResponse(
                requestDTO,
                target,
                paymentHistoryId,
                payAppResult
        );
    }

    private void authenticateRefundReservation(
            Long reservationId,
            String phone
    ) {
        NonMemberReservationAccessDTO accessDTO =
                new NonMemberReservationAccessDTO();
        accessDTO.setReservationId(reservationId);
        accessDTO.setPhone(phone);
        NonMemberReservationResponseDTO reservation =
                reservationService.getReservation(accessDTO);

        if (!"GUEST".equals(reservation.getReservationType())
                || reservation.getStatus() == null
                || !"CONFIRMED".equals(reservation.getStatus().name())
                || !"PAID".equals(reservation.getPaymentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "결제 완료된 비회원 확정 예약만 환불할 수 있습니다."
            );
        }
    }

    private String validateRefundRequest(
            Long reservationId,
            String phone,
            String cancelReason
    ) {
        if (reservationId == null || reservationId <= 0
                || isBlank(phone)
                || isBlank(cancelReason)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 ID, 연락처와 취소 사유가 필요합니다."
            );
        }
        String reason = cancelReason.trim();
        if (reason.length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "취소 사유는 500자 이하여야 합니다."
            );
        }
        return reason;
    }

    private void requirePreparedRefund(
            GuestReservationRefundPreparationDTO preparation
    ) {
        String resultCode = preparation == null
                ? null
                : preparation.getResultCode();
        if ("OK".equals(resultCode)) {
            return;
        }
        HttpStatus status = switch (String.valueOf(resultCode)) {
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
            case "ALREADY_REFUNDED", "REFUND_IN_PROGRESS",
                 "INVALID_STATUS", "CANCEL_CUTOFF_PASSED" ->
                    HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        String message = switch (String.valueOf(resultCode)) {
            case "NOT_FOUND" -> "비회원 예약을 찾을 수 없습니다.";
            case "INVALID_REQUEST" -> "환불 요청값을 확인해 주세요.";
            case "ALREADY_REFUNDED" -> "이미 환불된 결제입니다.";
            case "REFUND_IN_PROGRESS" -> "이미 환불 요청이 진행 중입니다.";
            case "INVALID_STATUS" -> "현재 예약 상태에서는 환불할 수 없습니다.";
            case "CANCEL_CUTOFF_PASSED" ->
                    "예약 시작 1시간 전까지만 환불할 수 있습니다.";
            case "PAYMENT_NOT_FOUND" -> "환불할 결제 내역을 찾을 수 없습니다.";
            case "MISSING_PROVIDER_ID" -> "결제사 거래번호가 없어 환불할 수 없습니다.";
            default -> "환불을 준비하지 못했습니다.";
        };
        throw new ResponseStatusException(status, message);
    }

    private Map<String, String> requestFullPayAppRefund(
            GuestReservationRefundPreparationDTO preparation,
            String cancelReason
    ) {
        if (isBlank(payAppUserid)
                || isBlank(payAppLinkKey)
                || isBlank(preparation.getPayAppPaymentId())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PayApp 환불 설정 또는 결제사 거래번호가 없습니다."
            );
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("cmd", "paycancel");
        form.put("userid", payAppUserid);
        form.put("linkkey", payAppLinkKey);
        form.put("mul_no", preparation.getPayAppPaymentId());
        form.put("cancelmemo", cancelReason);
        form.put("partcancel", "0");
        return payAppClient.postForm(form);
    }

    private void failPreparedRefund(
            Long refundHistoryId,
            String providerResponse
    ) {
        if (refundHistoryId == null) {
            return;
        }
        try {
            mapper.failGuestReservationRefund(
                    refundHistoryId,
                    providerResponse
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to persist guest reservation refund failure. refundHistoryId={}",
                    refundHistoryId,
                    exception
            );
        }
    }

    private void markRefundReconciliationRequired(
            Long refundHistoryId,
            String providerResponse
    ) {
        try {
            int marked = mapper
                    .markGuestReservationRefundReconciliationRequired(
                            refundHistoryId,
                            limitProviderResponse(providerResponse)
                    );
            if (marked != 1) {
                log.error(
                        "Guest refund reconciliation marker was not saved. refundHistoryId={}",
                        refundHistoryId
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Guest refund reconciliation marker failed. refundHistoryId={}",
                    refundHistoryId,
                    exception
            );
        }
    }

    private GuestReservationRefundResponseDTO toRefundResponse(
            GuestReservationRefundPreparationDTO preparation
    ) {
        GuestReservationRefundResponseDTO response =
                new GuestReservationRefundResponseDTO();
        response.setReservationId(preparation.getReservationId());
        response.setPaymentHistoryId(preparation.getPaymentHistoryId());
        response.setRefundHistoryId(preparation.getRefundHistoryId());
        response.setRefundAmount(preparation.getRefundAmount());
        response.setRefundStatus("SUCCESS");
        response.setReservationStatus("CANCELED");
        response.setPaymentStatus("REFUNDED");
        return response;
    }

    private String limitProviderResponse(String response) {
        if (response == null) {
            return null;
        }
        return response.length() <= 4000
                ? response
                : response.substring(0, 4000);
    }

    private Map<String, String> requestPayApp(
            Long paymentHistoryId,
            NonMemberReservationPaymentTargetDTO target
    ) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("cmd", "payrequest");
        form.put("userid", payAppUserid);
        form.put("goodname", target.getOrderName());
        form.put("price", String.valueOf(target.getAmount()));
        form.put("recvphone", target.getRecvPhone());
        form.put("memo", "Guest reservation prepayment");
        form.put("reqaddr", "0");
        form.put(
                "feedbackurl",
                backendBaseUrl
                        + "/api/public/non-member-reservations/payments/payapp/feedback"
        );
        form.put("var1", String.valueOf(paymentHistoryId));
        form.put(
                "var2",
                CONTEXT_PREFIX + target.getReservationId()
        );
        form.put("smsuse", "n");
        form.put(
                "returnurl",
                backendBaseUrl
                        + "/api/public/non-member-reservations/payments/payapp/return"
                        + "?reservationId=" + target.getReservationId()
        );
        form.put("skip_cstpage", "y");
        form.put("openpaytype", openPayType);
        form.put("checkretry", "y");
        return payAppClient.postForm(form);
    }

    private NonMemberReservationPaymentResponseDTO toResponse(
            NonMemberReservationPaymentRequestDTO requestDTO,
            NonMemberReservationPaymentTargetDTO target,
            Long paymentHistoryId,
            Map<String, String> payAppResult
    ) {
        NonMemberReservationPaymentResponseDTO response =
                new NonMemberReservationPaymentResponseDTO();
        response.setPaymentId(paymentHistoryId);
        response.setReservationId(target.getReservationId());
        response.setOrderId(
                "GUEST-RESERVATION-" + target.getReservationId()
        );
        response.setOrderName(target.getOrderName());
        response.setCustomerName(requestDTO.getCustomerName());
        response.setCustomerEmail(requestDTO.getCustomerEmail());
        response.setRecvPhone(target.getRecvPhone());
        response.setAmount(target.getAmount());
        response.setPayUrl(payAppResult.get("payurl"));
        response.setQrUrl(payAppResult.get("qrurl"));
        response.setStatus(PAYMENT_WAITING);
        return response;
    }

    private void authenticateReservation(
            NonMemberReservationPaymentRequestDTO requestDTO
    ) {
        NonMemberReservationAccessDTO accessDTO =
                new NonMemberReservationAccessDTO();
        accessDTO.setReservationId(requestDTO.getReservationId());
        accessDTO.setPhone(requestDTO.getPhone());
        reservationService.getReservation(accessDTO);
    }

    private void validateRequest(
            NonMemberReservationPaymentRequestDTO requestDTO
    ) {
        if (requestDTO == null
                || requestDTO.getReservationId() == null
                || requestDTO.getReservationId() <= 0
                || requestDTO.getPhone() == null
                || requestDTO.getPhone().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 ID와 연락처가 필요합니다."
            );
        }
    }

    private boolean isValidFeedback(Map<String, String> params) {
        if (params == null || params.isEmpty()
                || isBlank(payAppUserid)
                || isBlank(payAppLinkKey)
                || isBlank(payAppLinkValue)) {
            return false;
        }
        return secureEquals(payAppUserid, params.get("userid"))
                && secureEquals(payAppLinkKey, params.get("linkkey"))
                && secureEquals(payAppLinkValue, params.get("linkval"));
    }

    private boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String toPaymentStatus(String payState) {
        if (payState == null) {
            return null;
        }
        if ("4".equals(payState)) {
            return PAYMENT_SUCCESS;
        }
        if (Set.of("8", "9", "16", "31", "32", "64", "70", "71")
                .contains(payState)) {
            return PAYMENT_CANCELED;
        }
        if ("1".equals(payState) || "10".equals(payState)) {
            return PAYMENT_WAITING;
        }
        return null;
    }

    private String toPaymentType(String payType) {
        if (payType == null || payType.isBlank()) {
            return null;
        }
        return switch (payType) {
            case "1" -> "CARD";
            case "2" -> "PHONE";
            case "4" -> "FACE_TO_FACE";
            case "6" -> "BANK_TRANSFER";
            case "7" -> "VIRTUAL_ACCOUNT";
            case "15" -> "KAKAOPAY";
            case "16" -> "NAVERPAY";
            case "17" -> "BILLING";
            case "21" -> "SMILEPAY";
            case "22" -> "WECHATPAY";
            case "23" -> "APPLEPAY";
            case "24" -> "MY_ACCOUNT";
            case "25" -> "TOSSPAY";
            default -> "PAYAPP";
        };
    }

    private LocalDateTime parsePayDate(String payDate) {
        if (payDate == null || payDate.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        )) {
            try {
                return LocalDateTime.parse(payDate.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported PayApp date format.
            }
        }
        return null;
    }

    private Long parseReservationId(String context) {
        if (context == null || !context.startsWith(CONTEXT_PREFIX)) {
            return null;
        }
        return parseLong(context.substring(CONTEXT_PREFIX.length()));
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first;
        }
        return isBlank(second) ? null : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String limitReason(String reason) {
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }
}
