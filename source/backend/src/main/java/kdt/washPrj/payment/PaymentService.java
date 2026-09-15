package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentCancelDTO;
import kdt.washPrj.payment.dto.PaymentProviderResultDTO;
import kdt.washPrj.payment.dto.PaymentResponseDTO;
import kdt.washPrj.payment.dto.PaymentRequestPreparationDTO;
import kdt.washPrj.payment.dto.PaymentWaitingDTO;
import kdt.washPrj.payment.dto.PaymentWaitingResponseDTO;
import kdt.washPrj.payment.dto.RefundReasonCode;
import kdt.washPrj.payment.dto.KakaoPayReadyDTO;
import kdt.washPrj.payment.dto.TossPaymentReadyDTO;
import kdt.washPrj.payment.dto.PaymentRefundPreparationDTO;
import kdt.washPrj.payment.dto.PaymentReconciliationReviewDTO;
import kdt.washPrj.payment.dto.PaymentSearchCondition;
import kdt.washPrj.prepaid.PrepaidService;
import kdt.washPrj.prepaid.dto.PrepaidChargeRequestDTO;
import kdt.washPrj.prepaid.dto.PrepaidChargeResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String PAYMENT_WAITING = "WAITING";
    private static final String PAYMENT_SUCCESS = "SUCCESS";
    private static final String PAYMENT_CANCELED = "CANCELED";
    private static final String PAYMENT_PROVIDER_PAYAPP = "PAYAPP";
    private static final String DB_RESULT_APPLIED = "APPLIED";
    private static final String DB_RESULT_ALREADY_APPLIED = "ALREADY_APPLIED";
    private static final String DB_RESULT_NOT_FOUND = "NOT_FOUND";
    private static final String PREPAID_CHARGE_TYPE = "PREPAID_CHARGE";
    private static final String PURPOSE_CHARGE = "CHARGE";
    private static final String PURPOSE_WASH_PAYMENT = "WASH_PAYMENT";
    private static final String CHARGE_CONTEXT_PREFIX = "CHARGE:";
    private static final String AUTO_WASH_CONTEXT_PREFIX = "AUTO_WASH:";
    private static final String NON_MEMBER_CONTEXT_PREFIX = "NON_MEMBER:";
    private static final String REFUND_TYPE_WALLET_RESTORE = "WALLET_RESTORE";
    private static final String REFUND_TYPE_BENEFIT_ONLY = "BENEFIT_ONLY";
    private static final Set<String> MANUAL_PAYAPP_METHODS = Set.of(
            "CARD",
            "PHONE",
            "FACE_TO_FACE",
            "BANK_TRANSFER",
            "VIRTUAL_ACCOUNT",
            "KAKAOPAY",
            "NAVERPAY",
            "BILLING",
            "SMILEPAY",
            "WECHATPAY",
            "APPLEPAY",
            "MY_ACCOUNT",
            "TOSSPAY",
            "PAYAPP"
    );

    private final PaymentQueryMapper paymentQueryMapper;
    private final PaymentCommandMapper paymentCommandMapper;
    private final PrepaidService prepaidService;
    private final PaymentQueryService paymentQueryService;
    private final PayAppClient payAppClient;
    private final PaymentProviderRegistry paymentProviderRegistry;
    private final PaymentReconciliationService paymentReconciliationService;
    private final KakaoPayService kakaoPayService;
    private final TossPaymentsService tossPaymentsService;
    private final Set<Long> activeNonMemberPaymentRequests =
            ConcurrentHashMap.newKeySet();

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

    @Value("${payment.reconciliation.stale-seconds:${PAYMENT_RECONCILIATION_STALE_SECONDS:1800}}")
    private int reconciliationStaleSeconds;

    public PaymentService(
            PaymentQueryMapper paymentQueryMapper,
            PaymentCommandMapper paymentCommandMapper,
            PrepaidService prepaidService,
            PaymentQueryService paymentQueryService,
            PayAppClient payAppClient,
            PaymentProviderRegistry paymentProviderRegistry,
            PaymentReconciliationService paymentReconciliationService,
            KakaoPayService kakaoPayService,
            TossPaymentsService tossPaymentsService
    ) {
        this.paymentQueryMapper = paymentQueryMapper;
        this.paymentCommandMapper = paymentCommandMapper;
        this.prepaidService = prepaidService;
        this.paymentQueryService = paymentQueryService;
        this.payAppClient = payAppClient;
        this.paymentProviderRegistry = paymentProviderRegistry;
        this.paymentReconciliationService = paymentReconciliationService;
        this.kakaoPayService = kakaoPayService;
        this.tossPaymentsService = tossPaymentsService;
    }

    public PaymentWaitingDTO getCheckoutDefault(String loginId, String role) {
        requireCustomer(role);

        Long customerId = getCustomerId(loginId);
        PaymentWaitingDTO checkoutDefault =
                paymentQueryMapper.selectPrepaidChargeDefault(customerId);

        if (checkoutDefault == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer was not found."
            );
        }

        return checkoutDefault;
    }

    public PaymentWaitingResponseDTO waitingPayment(
            PaymentWaitingDTO requestDTO,
            String loginId,
            String role
    ) {
        requireCustomer(role);
        validatePrepaidChargeRequest(
                requestDTO.getAmount(),
                requestDTO.getOrderName(),
                requestDTO.getRecvPhone()
        );

        PrepaidChargeRequestDTO chargeRequest = new PrepaidChargeRequestDTO();
        chargeRequest.setAmount(requestDTO.getAmount());
        chargeRequest.setOrderName(requestDTO.getOrderName());
        chargeRequest.setRecvPhone(requestDTO.getRecvPhone());
        chargeRequest.setPaymentProvider(requestDTO.getPaymentProvider());

        PrepaidChargeResponseDTO chargeResponse =
                prepaidService.charge(chargeRequest, loginId, role);

        PaymentWaitingResponseDTO responseDTO =
                new PaymentWaitingResponseDTO();

        responseDTO.setPaymentId(chargeResponse.getPaymentHistoryId());
        responseDTO.setOrderId(
                chargeResponse.getOrderId() == null
                        ? "CHARGE-" + chargeResponse.getPaymentHistoryId()
                        : chargeResponse.getOrderId()
        );
        responseDTO.setOrderName(requestDTO.getOrderName());
        responseDTO.setCustomerId(String.valueOf(chargeResponse.getCustomerId()));
        responseDTO.setCustomerName(requestDTO.getCustomerName());
        responseDTO.setCustomerEmail(requestDTO.getCustomerEmail());
        responseDTO.setRecvPhone(requestDTO.getRecvPhone());
        responseDTO.setAmount(chargeResponse.getAmount());
        responseDTO.setPayUrl(chargeResponse.getPayUrl());
        responseDTO.setQrUrl(chargeResponse.getQrUrl());
        responseDTO.setAppUrl(chargeResponse.getAppUrl());
        responseDTO.setStatus(PAYMENT_WAITING);
        responseDTO.setPaymentProvider(chargeResponse.getPaymentProvider());
        responseDTO.setProviderClientKey(chargeResponse.getProviderClientKey());
        responseDTO.setSuccessUrl(chargeResponse.getSuccessUrl());
        responseDTO.setFailUrl(chargeResponse.getFailUrl());

        return responseDTO;
    }

    public List<PaymentResponseDTO> getPaymentList(
            String loginId,
            String role,
            PaymentSearchCondition condition
    ) {
        return paymentQueryService.getPaymentList(loginId, role, condition);
    }

    public PaymentResponseDTO getPayment(
            Long paymentId,
            String loginId,
            String role
    ) {
        return paymentQueryService.getPayment(paymentId, loginId, role);
    }

    public int cancelPayment(
            Long paymentId,
            PaymentCancelDTO requestDTO,
            String loginId,
            String role
    ) {
        return refundPayment(
                paymentId,
                requestDTO,
                loginId,
                role,
                null,
                null
        );
    }

    @Transactional
    public PaymentResponseDTO reviewPayAppReconciliation(
            Long paymentId,
            PaymentReconciliationReviewDTO request,
            String loginId,
            String role
    ) {
        requireAdmin(role);

        PaymentResponseDTO payment = paymentQueryMapper.selectPaymentById(paymentId);
        if (payment == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payment was not found."
            );
        }
        if (!PAYMENT_WAITING.equals(payment.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only a waiting payment can be manually reconciled."
            );
        }
        if (!PAYMENT_PROVIDER_PAYAPP.equalsIgnoreCase(
                payment.getPaymentProvider()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Manual reconciliation is limited to PayApp payments."
            );
        }
        if (payment.getProviderTransactionId() == null
                || payment.getProviderTransactionId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The PayApp transaction id is missing."
            );
        }

        String resolution = request.getResolution().trim().toUpperCase();
        String paymentMethod = request.getPaymentMethod() == null
                ? null
                : request.getPaymentMethod().trim().toUpperCase();

        if (PAYMENT_SUCCESS.equals(resolution)
                && (paymentMethod == null
                || !MANUAL_PAYAPP_METHODS.contains(paymentMethod))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A supported payment method is required for a confirmed payment."
            );
        }

        String providerStatus = PAYMENT_SUCCESS.equals(resolution)
                ? "MANUAL_CONFIRMED_SUCCESS"
                : "MANUAL_CONFIRMED_CANCELED";
        String reviewMessage = "Admin " + loginId + " confirmed PayApp "
                + resolution.toLowerCase() + ": "
                + request.getProviderProcessedAt() + ", "
                + request.getReviewNote().trim();

        PaymentProviderResultDTO result =
                paymentCommandMapper.applyProviderPaymentResult(
                        payment.getPaymentId(),
                        PAYMENT_PROVIDER_PAYAPP,
                        payment.getProviderTransactionId(),
                        providerStatus,
                        PAYMENT_SUCCESS.equals(resolution)
                                ? paymentMethod
                                : null,
                        payment.getAmount(),
                        resolution,
                        PAYMENT_SUCCESS.equals(resolution)
                                ? request.getProviderProcessedAt()
                                : null,
                        PAYMENT_CANCELED.equals(resolution)
                                ? reviewMessage
                                : null
                );

        if (result == null || (!DB_RESULT_APPLIED.equals(result.getResultCode())
                && !DB_RESULT_ALREADY_APPLIED.equals(result.getResultCode()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The reviewed provider result could not be applied."
            );
        }

        paymentCommandMapper.completePaymentReconciliation(
                payment.getPaymentId(),
                PAYMENT_CANCELED.equals(resolution)
                        ? "CANCELED_CONFIRMED"
                        : "REVIEW_REQUIRED",
                providerStatus,
                reviewMessage,
                300,
                1
        );

        return paymentQueryMapper.selectPaymentById(payment.getPaymentId());
    }

    public int refundPaymentForInquiry(
            Long paymentId,
            PaymentCancelDTO requestDTO,
            String loginId,
            String role,
            Long inquiryId,
            String inquiryAnswer
    ) {
        if (inquiryId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inquiry id is required."
            );
        }
        if (inquiryAnswer == null || inquiryAnswer.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inquiry refund answer is required."
            );
        }

        return refundPayment(
                paymentId,
                requestDTO,
                loginId,
                role,
                inquiryId,
                inquiryAnswer
        );
    }

    private int refundPayment(
            Long paymentId,
            PaymentCancelDTO requestDTO,
            String loginId,
            String role,
            Long inquiryId,
            String inquiryAnswer
    ) {
        requireManager(role);

        String reason = requestDTO == null
                ? "Manager refund"
                : requestDTO.getCancelReason();
        if (reason == null || reason.isBlank()) {
            reason = "Manager refund";
        }
        RefundReasonCode reasonCode = requestDTO == null
                || requestDTO.getRefundReasonCode() == null
                ? RefundReasonCode.CUSTOMER_REQUEST
                : requestDTO.getRefundReasonCode();
        reason = reasonCode.formatReason(reason);

        Integer requestedAmount = requestDTO == null
                ? null
                : requestDTO.getRefundAmount();

        PaymentRefundPreparationDTO preparation =
                paymentCommandMapper.preparePaymentRefund(
                        loginId,
                        paymentId,
                        requestedAmount,
                        reason,
                        inquiryId
                );

        if (preparation == null || "NOT_FOUND".equals(preparation.getResultCode())) {
            return 0;
        }

        requirePreparedRefund(preparation);

        if (REFUND_TYPE_WALLET_RESTORE.equals(preparation.getRefundType())
                || REFUND_TYPE_BENEFIT_ONLY.equals(preparation.getRefundType())) {
            return completePreparedRefund(
                    preparation,
                    "Wallet balance restored.",
                    inquiryAnswer
            );
        }

        String paymentProvider = firstNonBlank(
                preparation.getPaymentProvider(),
                PAYMENT_PROVIDER_PAYAPP
        );

        if (PaymentProviderRegistry.KAKAOPAY.equals(paymentProvider)) {
            try {
                String providerResponse = kakaoPayService.cancelApprovedPayment(
                        kakaoPayService.requireContext(paymentId),
                        preparation.getRefundAmount()
                );
                return completePreparedRefund(
                        preparation,
                        providerResponse,
                        inquiryAnswer
                );
            } catch (ResponseStatusException exception) {
                paymentCommandMapper.updateRefundHistoryResult(
                        preparation.getRefundHistoryId(),
                        "FAILED",
                        exception.getReason()
                );
                throw exception;
            }
        }

        if (PaymentProviderRegistry.TOSS_PAYMENTS.equals(paymentProvider)) {
            try {
                String providerResponse = tossPaymentsService.cancelApprovedPayment(
                        tossPaymentsService.requireContext(paymentId),
                        preparation.getRefundAmount(),
                        reason,
                        "refund-" + preparation.getRefundHistoryId()
                );
                return completePreparedRefund(
                        preparation,
                        providerResponse,
                        inquiryAnswer
                );
            } catch (ResponseStatusException exception) {
                paymentCommandMapper.updateRefundHistoryResult(
                        preparation.getRefundHistoryId(),
                        "FAILED",
                        exception.getReason()
                );
                throw exception;
            }
        }

        Map<String, String> response;
        try {
            response = requestPayAppCancel(
                    preparation.getPayappPaymentId(),
                    reason,
                    preparation.getRefundAmount(),
                    preparation.getOriginalAmount()
            );
        } catch (ResponseStatusException exception) {
            paymentCommandMapper.updateRefundHistoryResult(
                    preparation.getRefundHistoryId(),
                    "FAILED",
                    exception.getReason()
            );
            throw exception;
        }

        if (!"1".equals(response.get("state"))) {
            return failRefund(
                    preparation.getRefundHistoryId(),
                    response.getOrDefault(
                            "errorMessage",
                            "PayApp refund failed."
                    ),
                    HttpStatus.BAD_GATEWAY
            );
        }

        return completePreparedRefund(
                preparation,
                toProviderResponse(response),
                inquiryAnswer
        );
    }

    private void requirePreparedRefund(PaymentRefundPreparationDTO preparation) {
        String resultCode = preparation.getResultCode();
        if ("OK".equals(resultCode)) {
            return;
        }

        if ("FORBIDDEN".equals(resultCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        String message = switch (resultCode) {
            case "DUPLICATE_INQUIRY" -> "This inquiry has already been refunded.";
            case "INVALID_STATUS" -> "Only successful payments can be refunded.";
            case "ALREADY_REFUNDED" -> "The payment has already been fully refunded.";
            case "INVALID_AMOUNT" -> "Refund amount exceeds the remaining refundable amount.";
            case "INSUFFICIENT_BALANCE" -> "Refund amount exceeds the available prepaid balance.";
            case "MISSING_PROVIDER_ID" -> "Payment provider transaction number is missing.";
            case "FULL_REFUND_REQUIRED" -> "Mileage was used for this payment, so only a full refund is available.";
            default -> "Refund could not be prepared.";
        };
        throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private int completePreparedRefund(
            PaymentRefundPreparationDTO preparation,
            String providerResponse,
            String inquiryAnswer
    ) {
        try {
            int completed = inquiryAnswer == null
                    ? paymentCommandMapper.completePaymentRefund(
                            preparation.getRefundHistoryId(),
                            providerResponse
                    )
                    : paymentCommandMapper.completeInquiryPaymentRefund(
                            preparation.getRefundHistoryId(),
                            providerResponse,
                            inquiryAnswer
                    );
            if (completed == 1) {
                return 1;
            }
        } catch (RuntimeException exception) {
            recordRefundReconciliation(preparation, providerResponse, exception);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Refund provider processing succeeded, but local synchronization is pending.",
                    exception
            );
        }

        recordRefundReconciliation(preparation, providerResponse, null);
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Refund provider processing succeeded, but local synchronization is pending."
        );
    }

    private void recordRefundReconciliation(
            PaymentRefundPreparationDTO preparation,
            String providerResponse,
            RuntimeException exception
    ) {
        String message = providerResponse + ";localSync=PENDING";
        if (exception != null && exception.getMessage() != null) {
            message += ";message=" + exception.getMessage();
        }
        paymentCommandMapper.markRefundReconciliationRequired(
                preparation.getRefundHistoryId(),
                message
        );
    }

    private int failRefund(
            Long refundId,
            String message,
            HttpStatus status
    ) {
        paymentCommandMapper.updateRefundHistoryResult(
                refundId,
                "FAILED",
                message
        );
        throw new ResponseStatusException(status, message);
    }

    private Map<String, String> requestPayAppCancel(
            String payAppPaymentId,
            String reason,
            Integer refundAmount,
            Integer originalAmount
    ) {
        if (payAppLinkKey == null || payAppLinkKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PayApp link key is not configured."
            );
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("cmd", "paycancel");
        form.put("userid", payAppUserid);
        form.put("linkkey", payAppLinkKey);
        form.put("mul_no", payAppPaymentId);
        form.put("cancelmemo", reason);

        boolean partialCancel = refundAmount != null
                && originalAmount != null
                && refundAmount < originalAmount;
        form.put("partcancel", partialCancel ? "1" : "0");

        if (partialCancel) {
            form.put("cancelprice", String.valueOf(refundAmount));
        }

        return payAppClient.postForm(form);
    }

    private String toProviderResponse(Map<String, String> response) {
        return "state=" + response.getOrDefault("state", "")
                + ";errorMessage="
                + response.getOrDefault("errorMessage", "");
    }

    @Transactional
    public String handlePayAppFeedback(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            log.warn("PayApp feedback was empty.");
            return "FAIL";
        }

        if (!isValidPayAppFeedback(params)) {
            log.warn(
                    "PayApp feedback validation failed. useridPresent={}, linkkeyPresent={}, linkvalPresent={}",
                    params.containsKey("userid"),
                    params.containsKey("linkkey"),
                    params.containsKey("linkval")
            );
            return "FAIL";
        }

        Long paymentId = parseLong(params.get("var1"));
        Integer amount = parseInteger(params.get("price"));

        if (paymentId == null || amount == null) {
            log.warn(
                    "PayApp feedback missing required values. var1={}, price={}",
                    params.get("var1"),
                    params.get("price")
            );
            return "FAIL";
        }

        String status = toInternalPaymentStatus(params.get("pay_state"));

        if (status == null) {
            log.warn(
                    "PayApp feedback has an unsupported state. paymentId={}, payState={}",
                    paymentId,
                    params.get("pay_state")
            );
            return "FAIL";
        }

        if (isPartialCancellationState(params.get("pay_state"))) {
            return handlePartialCancellationFeedback(paymentId, params);
        }

        String method = toPayMethodName(params.get("pay_type"));
        LocalDateTime paidAt = parsePayDate(
                PAYMENT_CANCELED.equals(status)
                        ? firstNonBlank(params.get("canceldate"), params.get("pay_date"))
                        : params.get("pay_date")
        );
        String payAppPaymentId = firstNonBlank(
                params.get("mul_no"),
                params.get("payapp_transaction_id")
        );
        String cancelReason = firstNonBlank(
                params.get("cancelmemo"),
                params.get("cancel_reason")
        );

        Long autoWashHistoryId = parseAutoWashHistoryId(params.get("var2"));
        if (autoWashHistoryId == null) {
            PaymentProviderResultDTO providerResult =
                    paymentCommandMapper.applyProviderPaymentResult(
                            paymentId,
                            PAYMENT_PROVIDER_PAYAPP,
                            payAppPaymentId,
                            params.get("pay_state"),
                            method,
                            amount,
                            status,
                            paidAt,
                            cancelReason
                    );
            String providerResponse = toFeedbackResponse(providerResult, paymentId);
            if (providerResponse != null) {
                return providerResponse;
            }
        }

        Long nonMemberWashHistoryId =
                parseNonMemberWashHistoryId(params.get("var2"));

        if (nonMemberWashHistoryId != null
                && !isPrepaidChargeFeedback(params)) {
            return handleNonMemberPaymentFeedback(
                    paymentId,
                    nonMemberWashHistoryId,
                    amount,
                    method,
                    status,
                    paidAt,
                    payAppPaymentId,
                    cancelReason
            );
        }

        PaymentResponseDTO payment =
                paymentQueryMapper.selectPaymentByIdForUpdate(paymentId);
        boolean paymentCreated = false;

        if (payment == null) {
            if (!PAYMENT_SUCCESS.equals(status)) {
                if (autoWashHistoryId != null) {
                    paymentCommandMapper.releaseWashBenefitReservation(
                            autoWashHistoryId
                    );
                }
                log.info(
                        "PayApp non-success feedback ignored before payment creation. paymentId={}, payState={}",
                        paymentId,
                        params.get("pay_state")
                );
                return "SUCCESS";
            }

            int insertedCount = insertSuccessfulPaymentFromFeedback(
                    paymentId,
                    amount,
                    method,
                    paidAt,
                    payAppPaymentId,
                    params
            );
            if (insertedCount < 0) {
                return "FAIL";
            }

            paymentCreated = insertedCount == 1;
            payment = paymentQueryMapper.selectPaymentByIdForUpdate(paymentId);
        }

        if (payment == null || !amount.equals(payment.getAmount())) {
            log.warn(
                    "PayApp feedback payment lookup or amount check failed. paymentId={}, feedbackAmount={}, savedAmount={}",
                    paymentId,
                    amount,
                    payment == null ? null : payment.getAmount()
            );
            return "FAIL";
        }

        if (!paymentCreated && status.equals(payment.getStatus())) {
            log.info(
                    "Duplicate PayApp feedback ignored. paymentId={}, status={}",
                    paymentId,
                    status
            );
            return "SUCCESS";
        }

        if (isPrepaidChargeFeedback(params)) {
            return handlePrepaidChargeFeedback(paymentId, amount, params);
        }

        int feedbackResult = paymentCommandMapper.applyPaymentFeedbackAndWash(
                paymentId,
                method,
                amount,
                status,
                paidAt,
                payAppPaymentId,
                cancelReason
        );

        if (feedbackResult != 1) {
            log.warn(
                    "PayApp feedback local synchronization failed. paymentId={}, result={}",
                    paymentId,
                    feedbackResult
            );
            return "FAIL";
        }

        log.info(
                "PayApp feedback applied. paymentId={}, washHistoryId={}, method={}, status={}, amount={}, paidAt={}",
                paymentId,
                payment.getWashHistoryId(),
                method,
                status,
                amount,
                paidAt
        );

        return "SUCCESS";
    }

    private String toFeedbackResponse(
            PaymentProviderResultDTO providerResult,
            Long paymentId
    ) {
        if (providerResult == null
                || DB_RESULT_NOT_FOUND.equals(providerResult.getResultCode())) {
            return null;
        }

        if (DB_RESULT_APPLIED.equals(providerResult.getResultCode())
                || DB_RESULT_ALREADY_APPLIED.equals(providerResult.getResultCode())) {
            log.info(
                    "Provider-neutral payment result applied. paymentId={}, purpose={}, previousStatus={}, currentStatus={}, result={}",
                    paymentId,
                    providerResult.getPurpose(),
                    providerResult.getPreviousStatus(),
                    providerResult.getCurrentStatus(),
                    providerResult.getResultCode()
            );
            return "SUCCESS";
        }

        log.warn(
                "Provider-neutral payment result rejected. paymentId={}, result={}, previousStatus={}, currentStatus={}",
                paymentId,
                providerResult.getResultCode(),
                providerResult.getPreviousStatus(),
                providerResult.getCurrentStatus()
        );
        return "FAIL";
    }

    private String handlePartialCancellationFeedback(
            Long paymentId,
            Map<String, String> params
    ) {
        Integer cancelAmount = parseInteger(firstNonBlank(
                params.get("cancelprice"),
                params.get("price")
        ));

        if (cancelAmount == null || cancelAmount <= 0) {
            log.warn(
                    "PayApp partial cancellation amount is invalid. paymentId={}, cancelprice={}",
                    paymentId,
                    params.get("cancelprice")
            );
            return "FAIL";
        }

        if (paymentQueryMapper.existsSuccessfulRefund(
                paymentId,
                cancelAmount
        )) {
            log.info(
                    "PayApp partial cancellation callback confirmed. paymentId={}, amount={}",
                    paymentId,
                    cancelAmount
            );
            return "SUCCESS";
        }

        log.warn(
                "PayApp partial cancellation callback arrived before local refund completion. paymentId={}, amount={}",
                paymentId,
                cancelAmount
        );
        return "FAIL";
    }

    private String handleNonMemberPaymentFeedback(
            Long paymentId,
            Long washHistoryId,
            Integer amount,
            String method,
            String status,
            LocalDateTime paidAt,
            String payAppPaymentId,
            String cancelReason
    ) {
        PaymentResponseDTO payment =
                paymentQueryMapper.selectNonMemberPaymentForFeedback(
                        paymentId,
                        washHistoryId
                );

        if (payment != null && status.equals(payment.getStatus())) {
            log.info(
                    "Duplicate PayApp non-member feedback ignored. paymentId={}, washHistoryId={}, status={}",
                    paymentId,
                    washHistoryId,
                    status
            );
            return "SUCCESS";
        }

        boolean paymentCreated = false;

        if (payment == null) {
            if (!PAYMENT_SUCCESS.equals(status)) {
                log.info(
                        "PayApp non-member non-success feedback ignored before payment creation. paymentId={}, washHistoryId={}, status={}",
                        paymentId,
                        washHistoryId,
                        status
                );
                return "SUCCESS";
            }

            PaymentWaitingDTO target =
                    paymentQueryMapper.selectNonMemberCheckoutDefault(
                            washHistoryId
                    );

            if (target == null || !amount.equals(target.getAmount())) {
                log.warn(
                        "PayApp non-member feedback target check failed. paymentId={}, washHistoryId={}, feedbackAmount={}, targetAmount={}",
                        paymentId,
                        washHistoryId,
                        amount,
                        target == null ? null : target.getAmount()
                );
                return "FAIL";
            }

            paymentCreated = paymentCommandMapper.insertSuccessfulPaymentFromFeedback(
                    paymentId,
                    washHistoryId,
                    null,
                    amount,
                    method,
                    paidAt,
                    PURPOSE_WASH_PAYMENT,
                    payAppPaymentId
            ) == 1;

            payment = paymentQueryMapper.selectNonMemberPaymentForFeedback(
                    paymentId,
                    washHistoryId
            );
        }

        if (payment == null || !amount.equals(payment.getAmount())) {
            log.warn(
                    "PayApp non-member feedback payment lookup or amount check failed. paymentId={}, washHistoryId={}, feedbackAmount={}, savedAmount={}",
                    paymentId,
                    washHistoryId,
                    amount,
                    payment == null ? null : payment.getAmount()
            );
            return "FAIL";
        }

        if (!paymentCreated && status.equals(payment.getStatus())) {
            log.info(
                    "Duplicate PayApp non-member feedback ignored after concurrent insert. paymentId={}, washHistoryId={}, status={}",
                    paymentId,
                    washHistoryId,
                    status
            );
            return "SUCCESS";
        }

        int updated = paymentCommandMapper.applyNonMemberPaymentFeedbackAndWash(
                paymentId,
                washHistoryId,
                method,
                amount,
                status,
                paidAt,
                payAppPaymentId,
                cancelReason
        );

        if (updated != 1) {
            log.warn(
                    "PayApp non-member feedback update failed. paymentId={}, washHistoryId={}",
                    paymentId,
                    washHistoryId
            );
            return "FAIL";
        }

        log.info(
                "PayApp non-member feedback applied. paymentId={}, washHistoryId={}, method={}, status={}, amount={}, paidAt={}",
                paymentId,
                washHistoryId,
                method,
                status,
                amount,
                paidAt
        );

        return "SUCCESS";
    }

    private int insertSuccessfulPaymentFromFeedback(
            Long paymentHistoryId,
            Integer amount,
            String method,
            LocalDateTime paidAt,
            String payAppPaymentId,
            Map<String, String> params
    ) {
        String context = params.get("var2");
        Long customerId = null;
        Long washHistoryId = null;
        String purpose;

        if (isPrepaidChargeFeedback(params)) {
            customerId = parseChargeCustomerId(context);
            purpose = PURPOSE_CHARGE;
            Integer requestedAmount = parseChargeRequestedAmount(context);

            if (customerId == null || !amount.equals(requestedAmount)) {
                log.warn(
                        "PayApp charge feedback context check failed. paymentId={}, var2={}, feedbackAmount={}, requestedAmount={}",
                        paymentHistoryId,
                        context,
                        amount,
                        requestedAmount
                );
                return -1;
            }
        } else {
            washHistoryId = parseNonMemberWashHistoryId(context);
            purpose = PURPOSE_WASH_PAYMENT;

            PaymentWaitingDTO target = washHistoryId == null
                    ? null
                    : paymentQueryMapper.selectNonMemberCheckoutDefault(washHistoryId);

            if (target == null || !amount.equals(target.getAmount())) {
                log.warn(
                        "PayApp non-member feedback target check failed. paymentId={}, washHistoryId={}, feedbackAmount={}, targetAmount={}",
                        paymentHistoryId,
                        washHistoryId,
                        amount,
                        target == null ? null : target.getAmount()
                );
                return -1;
            }
        }

        return paymentCommandMapper.insertSuccessfulPaymentFromFeedback(
                paymentHistoryId,
                washHistoryId,
                customerId,
                amount,
                method,
                paidAt,
                purpose,
                payAppPaymentId
        );
    }

    private String cancelSuccessfulPrepaidChargeFromFeedback(
            Long paymentHistoryId,
            Integer amount,
            String method,
            LocalDateTime canceledAt,
            String payAppPaymentId,
            String cancelReason
    ) {
        String reason = firstNonBlank(
                cancelReason,
                "PayApp approval was canceled."
        );

        int result = paymentCommandMapper.cancelConfirmedPrepaidChargeFeedback(
                paymentHistoryId,
                method,
                amount,
                canceledAt,
                payAppPaymentId,
                reason
        );

        if (result != 1) {
            log.warn(
                    "PayApp canceled charge local synchronization failed. paymentHistoryId={}, result={}",
                    paymentHistoryId,
                    result
            );
            return "FAIL";
        }

        log.info(
                "PayApp canceled charge reversed from wallet. paymentHistoryId={}, amount={}",
                paymentHistoryId,
                amount
        );
        return "SUCCESS";
    }

    private String handlePrepaidChargeFeedback(
            Long paymentId,
            Integer amount,
            Map<String, String> params
    ) {
        PaymentResponseDTO charge =
                paymentQueryMapper.selectPrepaidChargeByPaymentHistoryId(paymentId);

        if (charge != null) {
            return handlePrepaidChargeByPaymentHistoryId(
                    paymentId,
                    amount,
                    params,
                    charge
            );
        }

        PaymentResponseDTO pendingCharge = paymentQueryMapper.selectPaymentById(paymentId);
        if (pendingCharge != null
                && PURPOSE_CHARGE.equals(pendingCharge.getPurpose())) {
            return handlePrepaidChargeByPaymentHistoryId(
                    paymentId,
                    amount,
                    params,
                    pendingCharge
            );
        }

        return handleLegacyPrepaidChargeByChargeId(
                paymentId,
                amount,
                params
        );
    }

    private String handlePrepaidChargeByPaymentHistoryId(
            Long paymentHistoryId,
            Integer amount,
            Map<String, String> params,
            PaymentResponseDTO charge
    ) {
        if (!amount.equals(charge.getAmount())) {
            log.warn(
                    "PayApp charge amount check failed. paymentHistoryId={}, feedbackAmount={}, savedAmount={}",
                    paymentHistoryId,
                    amount,
                    charge.getAmount()
            );
            return "FAIL";
        }

        String status = toInternalPaymentStatus(params.get("pay_state"));

        if (status == null) {
            log.warn(
                    "PayApp charge feedback has an unsupported state. paymentHistoryId={}, payState={}",
                    paymentHistoryId,
                    params.get("pay_state")
            );
            return "FAIL";
        }

        String method = toPayMethodName(params.get("pay_type"));
        LocalDateTime paidAt = parsePayDate(
                PAYMENT_CANCELED.equals(status)
                        ? firstNonBlank(params.get("canceldate"), params.get("pay_date"))
                        : params.get("pay_date")
        );
        String payAppPaymentId = firstNonBlank(
                params.get("mul_no"),
                params.get("payapp_transaction_id")
        );
        String cancelReason = firstNonBlank(
                params.get("cancelmemo"),
                params.get("cancel_reason")
        );

        if (PAYMENT_CANCELED.equals(status)
                && PAYMENT_SUCCESS.equals(charge.getStatus())) {
            return cancelSuccessfulPrepaidChargeFromFeedback(
                    paymentHistoryId,
                    amount,
                    method,
                    paidAt,
                    payAppPaymentId,
                    cancelReason
            );
        }

        int feedbackResult = paymentCommandMapper.applyPrepaidChargeFeedback(
                paymentHistoryId,
                method,
                amount,
                status,
                paidAt,
                payAppPaymentId,
                cancelReason
        );

        if (feedbackResult != 1) {
            log.warn(
                    "PayApp prepaid charge local synchronization failed. paymentHistoryId={}, result={}",
                    paymentHistoryId,
                    feedbackResult
            );
            return "FAIL";
        }

        Long autoWashHistoryId = parseAutoWashHistoryId(params.get("var2"));
        if (PAYMENT_SUCCESS.equals(status)) {
            if (autoWashHistoryId != null) {
                Long customerId = parseLong(charge.getCustomerId());
                var useResponse =
                        prepaidService.useAfterConfirmedAutoCharge(
                                customerId,
                                autoWashHistoryId
                        );

                if (useResponse == null) {
                    log.warn(
                            "PayApp auto billing succeeded but prepaid wash deduction was not completed. paymentHistoryId={}, washHistoryId={}",
                            paymentHistoryId,
                            autoWashHistoryId
                    );
                }
            }
        } else if (autoWashHistoryId != null) {
            paymentCommandMapper.releaseWashBenefitReservation(
                    autoWashHistoryId
            );
        }

        log.info(
                "PayApp prepaid charge feedback applied. paymentHistoryId={}, method={}, status={}, amount={}, paidAt={}",
                paymentHistoryId,
                method,
                status,
                amount,
                paidAt
        );

        return "SUCCESS";
    }

    private String handleLegacyPrepaidChargeByChargeId(
            Long chargeId,
            Integer amount,
            Map<String, String> params
    ) {
        PaymentResponseDTO charge =
                paymentQueryMapper.selectPrepaidChargeByIdForUpdate(chargeId);

        if (charge == null || !amount.equals(charge.getAmount())) {
            log.warn(
                    "PayApp legacy charge lookup or amount check failed. chargeId={}, feedbackAmount={}, savedAmount={}",
                    chargeId,
                    amount,
                    charge == null ? null : charge.getAmount()
            );
            return "FAIL";
        }

        String status = toInternalPaymentStatus(params.get("pay_state"));

        if (status == null) {
            log.warn(
                    "PayApp legacy charge feedback has an unsupported state. chargeId={}, payState={}",
                    chargeId,
                    params.get("pay_state")
            );
            return "FAIL";
        }

        String method = toPayMethodName(params.get("pay_type"));
        LocalDateTime paidAt = parsePayDate(params.get("pay_date"));

        if (PAYMENT_SUCCESS.equals(charge.getStatus())) {
            log.info(
                    "PayApp legacy charge feedback already applied. chargeId={}, amount={}",
                    chargeId,
                    amount
            );
            return "SUCCESS";
        }

        paymentCommandMapper.updatePrepaidChargeFromFeedback(
                chargeId,
                method,
                amount,
                status,
                paidAt,
                firstNonBlank(
                        params.get("cancelmemo"),
                        params.get("cancel_reason")
                )
        );

        if (PAYMENT_SUCCESS.equals(status)) {
            Long customerId = parseLong(charge.getCustomerId());

            if (customerId == null) {
                log.warn(
                        "PayApp legacy charge customer id parse failed. chargeId={}, customerId={}",
                        chargeId,
                        charge.getCustomerId()
                );
                return "FAIL";
            }

            paymentCommandMapper.ensurePrepaidWallet(customerId);
            paymentCommandMapper.increasePrepaidWallet(customerId, amount);
        }

        log.info(
                "PayApp legacy charge feedback applied. chargeId={}, customerId={}, method={}, status={}, amount={}, paidAt={}",
                chargeId,
                charge.getCustomerId(),
                method,
                status,
                amount,
                paidAt
        );

        return "SUCCESS";
    }

    private Map<String, String> requestNonMemberPayApp(
            Long paymentId,
            PaymentWaitingDTO requestDTO
    ) {
        Map<String, String> form = new LinkedHashMap<>();

        form.put("cmd", "payrequest");
        form.put("userid", payAppUserid);
        form.put("goodname", requestDTO.getOrderName());
        form.put("price", String.valueOf(requestDTO.getAmount()));
        form.put("recvphone", requestDTO.getRecvPhone());
        form.put("memo", "Non-member wash payment request");
        form.put("reqaddr", "0");
        form.put(
                "feedbackurl",
                backendBaseUrl + "/api/payments/payapp/feedback"
        );
        form.put("var1", String.valueOf(paymentId));
        form.put(
                "var2",
                NON_MEMBER_CONTEXT_PREFIX + requestDTO.getWashHistoryId()
        );
        form.put("smsuse", "n");
        form.put(
                "returnurl",
                backendBaseUrl
                        + "/api/public/payments/payapp/return/non-member?washHistoryId="
                        + requestDTO.getWashHistoryId()
        );
        form.put("skip_cstpage", "y");
        form.put("openpaytype", openPayType);
        form.put("checkretry", "y");

        return payAppClient.postForm(form);
    }

    private boolean isPrepaidChargeFeedback(Map<String, String> params) {
        String type = params.get("var2");
        return PURPOSE_CHARGE.equals(type)
                || PREPAID_CHARGE_TYPE.equals(type)
                || (type != null && type.startsWith(CHARGE_CONTEXT_PREFIX))
                || (type != null && type.startsWith(AUTO_WASH_CONTEXT_PREFIX));
    }

    private Long parseChargeCustomerId(String value) {
        if (value == null) {
            return null;
        }

        if (value.startsWith(CHARGE_CONTEXT_PREFIX)) {
            String[] context = value
                    .substring(CHARGE_CONTEXT_PREFIX.length())
                    .split(":", 2);
            return parseLong(context[0]);
        }

        if (value.startsWith(AUTO_WASH_CONTEXT_PREFIX)) {
            String[] context = value
                    .substring(AUTO_WASH_CONTEXT_PREFIX.length())
                    .split(":", 3);
            return context.length >= 2 ? parseLong(context[0]) : null;
        }

        return null;
    }

    private Long parseAutoWashHistoryId(String value) {
        if (value == null || !value.startsWith(AUTO_WASH_CONTEXT_PREFIX)) {
            return null;
        }

        String[] context = value
                .substring(AUTO_WASH_CONTEXT_PREFIX.length())
                .split(":", 3);

        return parseLong(context.length >= 2 ? context[1] : context[0]);
    }

    private Integer parseChargeRequestedAmount(String value) {
        if (value == null) {
            return null;
        }

        if (value.startsWith(CHARGE_CONTEXT_PREFIX)) {
            String[] context = value
                    .substring(CHARGE_CONTEXT_PREFIX.length())
                    .split(":", 2);
            return context.length == 2 ? parseInteger(context[1]) : null;
        }

        if (value.startsWith(AUTO_WASH_CONTEXT_PREFIX)) {
            String[] context = value
                    .substring(AUTO_WASH_CONTEXT_PREFIX.length())
                    .split(":", 3);
            return context.length == 3 ? parseInteger(context[2]) : null;
        }

        return null;
    }

    private Long parseNonMemberWashHistoryId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        if (value.startsWith(NON_MEMBER_CONTEXT_PREFIX)) {
            return parseLong(value.substring(NON_MEMBER_CONTEXT_PREFIX.length()));
        }

        return parseLong(value);
    }

    private boolean isValidPayAppFeedback(Map<String, String> params) {
        if (!hasPayAppFeedbackCredentials()) {
            log.error("PayApp feedback credentials are not configured.");
            return false;
        }

        return secureEquals(payAppUserid, params.get("userid"))
                && secureEquals(payAppLinkKey, params.get("linkkey"))
                && secureEquals(payAppLinkValue, params.get("linkval"));
    }

    private boolean hasPayAppFeedbackCredentials() {
        return payAppUserid != null && !payAppUserid.isBlank()
                && payAppLinkKey != null && !payAppLinkKey.isBlank()
                && payAppLinkValue != null && !payAppLinkValue.isBlank();
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

    private String toInternalPaymentStatus(String payState) {
        if ("4".equals(payState)) {
            return PAYMENT_SUCCESS;
        }

        if ("8".equals(payState)
                || "16".equals(payState)
                || "31".equals(payState)
                || "9".equals(payState)
                || "32".equals(payState)
                || "64".equals(payState)
                || "70".equals(payState)
                || "71".equals(payState)) {
            return PAYMENT_CANCELED;
        }

        if ("1".equals(payState) || "10".equals(payState)) {
            return PAYMENT_WAITING;
        }

        return null;
    }

    private boolean isPartialCancellationState(String payState) {
        return "70".equals(payState) || "71".equals(payState);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }

        return second != null && !second.isBlank() ? second : null;
    }

    private String toPayMethodName(String payType) {
        if (payType == null || payType.isBlank()) {
            return null;
        }

        if ("1".equals(payType)) {
            return "CARD";
        }

        if ("2".equals(payType)) {
            return "PHONE";
        }

        if ("4".equals(payType)) {
            return "FACE_TO_FACE";
        }

        if ("6".equals(payType)) {
            return "BANK_TRANSFER";
        }

        if ("7".equals(payType)) {
            return "VIRTUAL_ACCOUNT";
        }

        if ("15".equals(payType)) {
            return "KAKAOPAY";
        }

        if ("16".equals(payType)) {
            return "NAVERPAY";
        }

        if ("17".equals(payType)) {
            return "BILLING";
        }

        if ("21".equals(payType)) {
            return "SMILEPAY";
        }

        if ("22".equals(payType)) {
            return "WECHATPAY";
        }

        if ("23".equals(payType)) {
            return "APPLEPAY";
        }

        if ("24".equals(payType)) {
            return "MY_ACCOUNT";
        }

        if ("25".equals(payType)) {
            return "TOSSPAY";
        }

        return "PAYAPP";
    }

    private LocalDateTime parsePayDate(String payDate) {
        if (payDate == null || payDate.isBlank()) {
            return null;
        }

        String normalized = payDate.trim();

        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        )) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next PayApp date format candidate.
            }
        }

        log.warn("PayApp pay_date parse failed. pay_date={}", payDate);
        return null;
    }

    private Long getCustomerId(String loginId) {
        Long customerId = paymentQueryMapper.selectCustomerIdByLoginId(loginId);

        if (customerId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Customer account is required."
            );
        }

        return customerId;
    }

    private void validatePaymentRequest(
            Long washHistoryId,
            Integer amount,
            String orderName,
            String recvPhone
    ) {
        if (washHistoryId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "washHistoryId is required."
            );
        }

        validatePrepaidChargeRequest(amount, orderName, recvPhone);
    }

    private void validatePrepaidChargeRequest(
            Integer amount,
            String orderName,
            String recvPhone
    ) {
        if (amount == null || amount < 1000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "amount must be at least 1000."
            );
        }

        requireText(orderName, "orderName is required.");
        requireText(recvPhone, "recvPhone is required.");
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private Integer parseInteger(String value) {
        try {
            return value == null || value.isBlank()
                    ? null
                    : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank()
                    ? null
                    : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void requireCustomer(String role) {
        if (!"CUSTOMER".equals(toPaymentRole(role))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(toPaymentRole(role))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void requireManager(String role) {
        String paymentRole = toPaymentRole(role);

        if (!"ADMIN".equals(paymentRole) && !"OWNER".equals(paymentRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private String toPaymentRole(String role) {
        if ("ROLE_ADMIN".equals(role) || "ADMIN".equals(role)) {
            return "ADMIN";
        }

        if ("ROLE_OWNER".equals(role) || "OWNER".equals(role)) {
            return "OWNER";
        }

        if ("ROLE_CUSTOMER".equals(role) || "CUSTOMER".equals(role)) {
            return "CUSTOMER";
        }

        return "";
    }

    public PaymentWaitingDTO getNonMemberCheckoutDefault(
            Long washHistoryId
    ) {
        PaymentWaitingDTO result =
                paymentQueryMapper.selectNonMemberCheckoutDefault(washHistoryId);

        if (result == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payable non-member wash history was not found."
            );
        }

        return result;
    }

    public PaymentWaitingResponseDTO waitingNonMemberPayment(
            PaymentWaitingDTO requestDTO
    ) {
        validatePaymentRequest(
                requestDTO.getWashHistoryId(),
                requestDTO.getAmount(),
                requestDTO.getOrderName(),
                requestDTO.getRecvPhone()
        );

        Long washHistoryId = requestDTO.getWashHistoryId();
        if (!activeNonMemberPaymentRequests.add(washHistoryId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A non-member payment request is already in progress."
            );
        }

        try {
            return requestNonMemberPayment(requestDTO);
        } finally {
            activeNonMemberPaymentRequests.remove(washHistoryId);
        }
    }

    public PaymentResponseDTO abandonNonMemberPayment(
            Long paymentId,
            Long washHistoryId
    ) {
        if (paymentId == null || washHistoryId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "paymentId and washHistoryId are required."
            );
        }

        PaymentResponseDTO payment =
                paymentQueryMapper.selectNonMemberPaymentForFeedback(
                        paymentId,
                        washHistoryId
                );
        if (payment == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Non-member payment was not found."
            );
        }
        if (!PURPOSE_WASH_PAYMENT.equals(payment.getPurpose())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only non-member wash payments can be abandoned."
            );
        }

        if (PAYMENT_WAITING.equals(payment.getStatus())) {
            paymentReconciliationService.abandonWaitingPaymentForRetry(
                    paymentId
            );
        }

        return paymentQueryMapper.selectNonMemberPaymentForFeedback(
                paymentId,
                washHistoryId
        );
    }

    private PaymentWaitingResponseDTO requestNonMemberPayment(
            PaymentWaitingDTO requestDTO
    ) {
        String paymentProvider = paymentProviderRegistry.requireAvailable(
                requestDTO.getPaymentProvider()
        );
        PaymentRequestPreparationDTO preparation =
                paymentCommandMapper.prepareNonMemberPaymentRequest(
                        requestDTO.getWashHistoryId(),
                        paymentProvider
                );

        if (preparation != null
                && "ALREADY_WAITING".equals(preparation.getResultCode())) {
            boolean existingRequestClosed =
                    paymentReconciliationService.cancelPayAppWaitingPaymentForRetry(
                            preparation.getPaymentHistoryId(),
                            paymentProvider
                    )
                    || paymentReconciliationService.reconcileStalePaymentNow(
                            preparation.getPaymentHistoryId()
                    );
            if (existingRequestClosed) {
                preparation = paymentCommandMapper.prepareNonMemberPaymentRequest(
                        requestDTO.getWashHistoryId(),
                        paymentProvider
                );
            }
        }

        boolean resumeExistingRequest = preparation != null
                && "ALREADY_WAITING".equals(preparation.getResultCode())
                && paymentReconciliationService.isResumableWaitingPayment(
                        preparation.getPaymentHistoryId(),
                        paymentProvider,
                        preparation.getAmount()
                );

        if (preparation == null
                || "NOT_FOUND".equals(preparation.getResultCode())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payable non-member wash history was not found."
            );
        }
        if (!"OK".equals(preparation.getResultCode())
                && !resumeExistingRequest) {
            if ("ALREADY_PAID".equals(preparation.getResultCode())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The non-member wash history is already paid."
                );
            }
            if ("ALREADY_WAITING".equals(preparation.getResultCode())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A non-member payment request is already waiting."
                );
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The non-member payment target is invalid."
            );
        }

        requestDTO.setOrderName(preparation.getOrderName());
        requestDTO.setAmount(preparation.getAmount());

        Long paymentHistoryId = preparation.getPaymentHistoryId();

        if (PaymentProviderRegistry.KAKAOPAY.equals(paymentProvider)) {
            try {
                KakaoPayReadyDTO ready = kakaoPayService.readyNonMember(
                        paymentHistoryId,
                        requestDTO.getWashHistoryId(),
                        requestDTO.getOrderName(),
                        requestDTO.getAmount()
                );
                PaymentWaitingResponseDTO response = baseNonMemberResponse(
                        paymentHistoryId,
                        requestDTO,
                        paymentProvider
                );
                response.setPayUrl(ready.getPayUrl());
                response.setQrUrl(ready.getQrUrl());
                response.setAppUrl(ready.getAppUrl());
                return response;
            } catch (RuntimeException exception) {
                markPaymentRequestFailed(
                        paymentHistoryId,
                        paymentProvider,
                        "REQUEST_EXCEPTION",
                        exception.getMessage()
                );
                throw exception;
            }
        }

        if (PaymentProviderRegistry.TOSS_PAYMENTS.equals(paymentProvider)) {
            try {
                TossPaymentReadyDTO ready = tossPaymentsService.ready(
                        paymentHistoryId
                );
                PaymentWaitingResponseDTO response = baseNonMemberResponse(
                        paymentHistoryId,
                        requestDTO,
                        paymentProvider
                );
                response.setOrderId(ready.getOrderId());
                response.setProviderClientKey(ready.getClientKey());
                response.setSuccessUrl(ready.getSuccessUrl());
                response.setFailUrl(ready.getFailUrl());
                response.setPayUrl(tossPaymentsService.checkoutUrl(
                        ready,
                        requestDTO.getOrderName(),
                        requestDTO.getAmount()
                ));
                return response;
            } catch (RuntimeException exception) {
                markPaymentRequestFailed(
                        paymentHistoryId,
                        paymentProvider,
                        "REQUEST_EXCEPTION",
                        exception.getMessage()
                );
                throw exception;
            }
        }

        Map<String, String> payAppResult;
        try {
            payAppResult = requestNonMemberPayApp(
                    paymentHistoryId,
                    requestDTO
            );
        } catch (RuntimeException exception) {
            markPaymentRequestFailed(
                    paymentHistoryId,
                    paymentProvider,
                    "REQUEST_EXCEPTION",
                    exception.getMessage()
            );
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "PayApp non-member payment request failed."
            );
        }

        if (!"1".equals(payAppResult.get("state"))) {
            markPaymentRequestFailed(
                    paymentHistoryId,
                    paymentProvider,
                    payAppResult.get("state"),
                    payAppResult.get("errorMessage")
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    payAppResult.getOrDefault(
                            "errorMessage",
                            "PayApp non-member payment request failed."
                    )
            );
        }

        recordProviderRequest(
                paymentHistoryId,
                payAppResult.get("mul_no"),
                "REQUEST_ACCEPTED"
        );

        PaymentWaitingResponseDTO response = baseNonMemberResponse(
                paymentHistoryId,
                requestDTO,
                paymentProvider
        );
        response.setPayUrl(payAppResult.get("payurl"));
        response.setQrUrl(payAppResult.get("qrurl"));

        return response;
    }

    private PaymentWaitingResponseDTO baseNonMemberResponse(
            Long paymentHistoryId,
            PaymentWaitingDTO requestDTO,
            String paymentProvider
    ) {
        PaymentWaitingResponseDTO response = new PaymentWaitingResponseDTO();
        response.setPaymentId(paymentHistoryId);
        response.setOrderId(String.valueOf(paymentHistoryId));
        response.setOrderName(requestDTO.getOrderName());
        response.setCustomerName(requestDTO.getCustomerName());
        response.setCustomerEmail(requestDTO.getCustomerEmail());
        response.setRecvPhone(requestDTO.getRecvPhone());
        response.setAmount(requestDTO.getAmount());
        response.setStatus(PAYMENT_WAITING);
        response.setPaymentProvider(paymentProvider);
        return response;
    }

    private void recordProviderRequest(
            Long paymentHistoryId,
            String providerTransactionId,
            String providerStatus
    ) {
        try {
            String result = paymentCommandMapper.recordPaymentProviderRequest(
                    paymentHistoryId,
                    PAYMENT_PROVIDER_PAYAPP,
                    providerTransactionId,
                    providerStatus,
                    reconciliationStaleSeconds
            );
            if (!"RECORDED".equals(result) && !"ALREADY_FINAL".equals(result)) {
                log.warn(
                        "Provider request requires review. paymentHistoryId={}, result={}",
                        paymentHistoryId,
                        result
                );
            }
        } catch (RuntimeException exception) {
            // The provider accepted the request, so keep it pending for reconciliation.
            log.error(
                    "Provider request id persistence failed. paymentHistoryId={}",
                    paymentHistoryId,
                    exception
            );
        }
    }

    private void markPaymentRequestFailed(
            Long paymentHistoryId,
            String paymentProvider,
            String providerStatus,
            String failureReason
    ) {
        try {
            int updated = paymentCommandMapper.markPaymentRequestFailed(
                    paymentHistoryId,
                    paymentProvider,
                    providerStatus,
                    failureReason
            );
            if (updated != 1) {
                log.warn(
                        "Payment request failure was not persisted. paymentHistoryId={}, providerStatus={}",
                        paymentHistoryId,
                        providerStatus
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Payment request failure persistence failed. paymentHistoryId={}",
                    paymentHistoryId,
                    exception
            );
        }
    }

    public List<PaymentResponseDTO> getNonMemberPaymentList(
            Long washHistoryId
    ) {
        return paymentQueryMapper.selectNonMemberPaymentList(washHistoryId);
    }
}
