package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentReconciliationCandidateDTO;
import kdt.washPrj.payment.dto.PaymentProviderResultDTO;
import kdt.washPrj.payment.dto.PaymentResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);
    private static final String PROVIDER_PAYAPP = "PAYAPP";
    private static final String PROVIDER_KAKAOPAY = "KAKAOPAY";
    private static final String PROVIDER_TOSS_PAYMENTS = "TOSSPAYMENTS";

    private final PaymentCommandMapper paymentCommandMapper;
    private final PaymentQueryMapper paymentQueryMapper;
    private final PayAppClient payAppClient;
    private final KakaoPayService kakaoPayService;
    private final TossPaymentsService tossPaymentsService;

    @Value("${payapp.userid:}")
    private String payAppUserid;

    @Value("${payapp.link-key:}")
    private String payAppLinkKey;

    @Value("${payment.reconciliation.enabled:${PAYMENT_RECONCILIATION_ENABLED:true}}")
    private boolean enabled;

    @Value("${payment.reconciliation.stale-seconds:${PAYMENT_RECONCILIATION_STALE_SECONDS:1800}}")
    private int staleSeconds;

    @Value("${payment.reconciliation.retry-seconds:${PAYMENT_RECONCILIATION_RETRY_SECONDS:300}}")
    private int retrySeconds;

    @Value("${payment.reconciliation.lease-seconds:${PAYMENT_RECONCILIATION_LEASE_SECONDS:300}}")
    private int leaseSeconds;

    @Value("${payment.reconciliation.max-attempts:${PAYMENT_RECONCILIATION_MAX_ATTEMPTS:5}}")
    private int maxAttempts;

    @Value("${payment.reconciliation.batch-size:${PAYMENT_RECONCILIATION_BATCH_SIZE:20}}")
    private int batchSize;

    public PaymentReconciliationService(
            PaymentCommandMapper paymentCommandMapper,
            PaymentQueryMapper paymentQueryMapper,
            PayAppClient payAppClient,
            KakaoPayService kakaoPayService,
            TossPaymentsService tossPaymentsService
    ) {
        this.paymentCommandMapper = paymentCommandMapper;
        this.paymentQueryMapper = paymentQueryMapper;
        this.payAppClient = payAppClient;
        this.kakaoPayService = kakaoPayService;
        this.tossPaymentsService = tossPaymentsService;
    }

    @Scheduled(
            fixedDelayString = "${payment.reconciliation.fixed-delay-ms:${PAYMENT_RECONCILIATION_FIXED_DELAY_MS:60000}}",
            initialDelayString = "${payment.reconciliation.initial-delay-ms:${PAYMENT_RECONCILIATION_INITIAL_DELAY_MS:60000}}"
    )
    public void reconcileExpiredRequests() {
        if (!enabled) {
            return;
        }

        try {
            paymentCommandMapper.markUntrackablePaymentReconciliations(
                    LocalDateTime.now().minusSeconds(Math.max(staleSeconds, 60)),
                    Math.max(batchSize, 1)
            );

            List<PaymentReconciliationCandidateDTO> candidates =
                    paymentCommandMapper.claimPaymentReconciliationCandidates(
                            Math.max(batchSize, 1),
                            Math.max(leaseSeconds, 60)
                    );
            if (candidates == null) {
                return;
            }
            candidates.forEach(this::reconcileCandidate);
        } catch (RuntimeException exception) {
            log.error("Payment reconciliation batch failed.", exception);
        }
    }

    public boolean reconcileStalePaymentNow(Long paymentHistoryId) {
        if (!enabled || paymentHistoryId == null) {
            return false;
        }

        PaymentResponseDTO payment = paymentQueryMapper.selectPaymentById(
                paymentHistoryId
        );
        if (payment == null) {
            return false;
        }
        if (!"WAITING".equals(payment.getStatus())) {
            return true;
        }
        if (!isProviderRequestExpired(payment)) {
            return false;
        }
        if (payment.getPaymentProvider() == null
                || payment.getPaymentProvider().isBlank()
                || payment.getProviderTransactionId() == null
                || payment.getProviderTransactionId().isBlank()) {
            return false;
        }

        PaymentReconciliationCandidateDTO candidate =
                new PaymentReconciliationCandidateDTO();
        candidate.setPaymentHistoryId(payment.getPaymentId());
        candidate.setPaymentProvider(payment.getPaymentProvider());
        candidate.setProviderTransactionId(
                payment.getProviderTransactionId()
        );
        candidate.setAmount(payment.getAmount());
        candidate.setPurpose(payment.getPurpose());
        candidate.setReconciliationAttempts(0);

        reconcileCandidate(candidate);

        PaymentResponseDTO updated = paymentQueryMapper.selectPaymentById(
                paymentHistoryId
        );
        return updated != null && !"WAITING".equals(updated.getStatus());
    }

    public boolean isResumableWaitingPayment(
            Long paymentHistoryId,
            String paymentProvider,
            Integer amount
    ) {
        if (paymentHistoryId == null
                || paymentProvider == null
                || amount == null
                || !PROVIDER_TOSS_PAYMENTS.equalsIgnoreCase(paymentProvider)) {
            return false;
        }

        PaymentResponseDTO payment = paymentQueryMapper.selectPaymentById(
                paymentHistoryId
        );
        return payment != null
                && "WAITING".equals(payment.getStatus())
                && PROVIDER_TOSS_PAYMENTS.equalsIgnoreCase(
                        payment.getPaymentProvider()
                )
                && amount.equals(payment.getAmount());
    }

    public boolean cancelPayAppWaitingPaymentForRetry(
            Long paymentHistoryId,
            String paymentProvider
    ) {
        if (paymentHistoryId == null
                || !PROVIDER_PAYAPP.equalsIgnoreCase(paymentProvider)
                || !hasPayAppCredentials()) {
            return false;
        }

        PaymentResponseDTO payment = paymentQueryMapper.selectPaymentById(
                paymentHistoryId
        );
        if (payment == null) {
            return false;
        }
        if (!"WAITING".equals(payment.getStatus())) {
            return true;
        }
        if (!PROVIDER_PAYAPP.equalsIgnoreCase(payment.getPaymentProvider())
                || payment.getProviderTransactionId() == null
                || payment.getProviderTransactionId().isBlank()) {
            return false;
        }

        try {
            Map<String, String> response = payAppClient.postForm(
                    createReadyCancelForm(payment.getProviderTransactionId())
            );
            if (!"1".equals(response.get("state"))) {
                log.info(
                        "PayApp waiting payment could not be canceled for retry. paymentHistoryId={}, state={}, message={}",
                        paymentHistoryId,
                        response.get("state"),
                        response.get("errorMessage")
                );
                return false;
            }

            PaymentReconciliationCandidateDTO candidate =
                    new PaymentReconciliationCandidateDTO();
            candidate.setPaymentHistoryId(payment.getPaymentId());
            candidate.setPaymentProvider(payment.getPaymentProvider());
            candidate.setProviderTransactionId(
                    payment.getProviderTransactionId()
            );
            candidate.setAmount(payment.getAmount());
            candidate.setPurpose(payment.getPurpose());
            candidate.setReconciliationAttempts(0);
            complete(
                    candidate,
                    "CANCELED_CONFIRMED",
                    "READY_CANCEL_CONFIRMED",
                    "PayApp unpaid request was canceled before retry."
            );

            PaymentResponseDTO updated = paymentQueryMapper.selectPaymentById(
                    paymentHistoryId
            );
            return updated != null && !"WAITING".equals(updated.getStatus());
        } catch (RuntimeException exception) {
            log.warn(
                    "PayApp waiting payment cancellation failed before retry. paymentHistoryId={}",
                    paymentHistoryId,
                    exception
            );
            return false;
        }
    }

    public boolean abandonWaitingPaymentForRetry(Long paymentHistoryId) {
        if (paymentHistoryId == null) {
            return false;
        }

        PaymentResponseDTO payment = paymentQueryMapper.selectPaymentById(
                paymentHistoryId
        );
        if (payment == null) {
            return false;
        }
        if (!"WAITING".equals(payment.getStatus())) {
            return true;
        }

        String provider = firstNonBlank(payment.getPaymentProvider()).toUpperCase();
        if (PROVIDER_PAYAPP.equals(provider)) {
            return cancelPayAppWaitingPaymentForRetry(
                    paymentHistoryId,
                    provider
            );
        }
        if ((!PROVIDER_KAKAOPAY.equals(provider)
                && !PROVIDER_TOSS_PAYMENTS.equals(provider))
                || payment.getProviderTransactionId() == null
                || payment.getProviderTransactionId().isBlank()) {
            return false;
        }

        PaymentReconciliationCandidateDTO candidate =
                new PaymentReconciliationCandidateDTO();
        candidate.setPaymentHistoryId(payment.getPaymentId());
        candidate.setPaymentProvider(provider);
        candidate.setProviderTransactionId(payment.getProviderTransactionId());
        candidate.setAmount(payment.getAmount());
        candidate.setPurpose(payment.getPurpose());
        candidate.setReconciliationAttempts(0);

        reconcileCandidate(candidate);

        PaymentResponseDTO updated = paymentQueryMapper.selectPaymentById(
                paymentHistoryId
        );
        return updated != null && !"WAITING".equals(updated.getStatus());
    }

    private boolean isProviderRequestExpired(PaymentResponseDTO payment) {
        if (payment.getRequestedAt() == null) {
            return false;
        }

        int providerExpirationSeconds = Math.max(staleSeconds, 60);
        if (PROVIDER_KAKAOPAY.equalsIgnoreCase(
                payment.getPaymentProvider()
        )) {
            providerExpirationSeconds = Math.min(
                    providerExpirationSeconds,
                    900
            );
        }

        return !payment.getRequestedAt().isAfter(
                LocalDateTime.now().minusSeconds(providerExpirationSeconds)
        );
    }

    private void reconcileCandidate(PaymentReconciliationCandidateDTO candidate) {
        String provider = firstNonBlank(candidate.getPaymentProvider()).toUpperCase();

        if (PROVIDER_KAKAOPAY.equals(provider)) {
            reconcileKakaoPay(candidate);
            return;
        }
        if (PROVIDER_TOSS_PAYMENTS.equals(provider)) {
            reconcileTossPayments(candidate);
            return;
        }
        if (!PROVIDER_PAYAPP.equals(provider)) {
            complete(candidate, "REVIEW_REQUIRED", "UNSUPPORTED_PROVIDER",
                    "Automatic reconciliation is not configured for this provider.");
            return;
        }
        if (!hasPayAppCredentials()) {
            complete(candidate, "RETRY", "MISSING_CREDENTIALS",
                    "PayApp credentials are not configured.");
            return;
        }

        try {
            Map<String, String> response = payAppClient.postForm(
                    createReadyCancelForm(candidate.getProviderTransactionId())
            );
            if ("1".equals(response.get("state"))) {
                complete(
                        candidate,
                        "CANCELED_CONFIRMED",
                        "READY_CANCEL_CONFIRMED",
                        "PayApp confirmed cancellation while the request was still unpaid."
                );
                return;
            }

            complete(
                    candidate,
                    "RETRY",
                    firstNonBlank(response.get("errno"), response.get("state"), "UNKNOWN"),
                    firstNonBlank(response.get("errorMessage"), "PayApp payment state could not be confirmed.")
            );
        } catch (RuntimeException exception) {
            complete(
                    candidate,
                    "RETRY",
                    "REQUEST_ERROR",
                    firstNonBlank(exception.getMessage(), "PayApp reconciliation request failed.")
            );
        }
    }

    private void reconcileKakaoPay(
            PaymentReconciliationCandidateDTO candidate
    ) {
        try {
            Map<String, Object> response = kakaoPayService.getOrder(
                    candidate.getProviderTransactionId()
            );
            String status = objectText(response.get("status"));
            if ("SUCCESS_PAYMENT".equals(status)) {
                if (!candidate.getProviderTransactionId().equals(
                        objectText(response.get("tid"))
                ) || !candidate.getAmount().equals(
                        nestedInteger(response, "amount", "total")
                )) {
                    complete(candidate, "REVIEW_REQUIRED", status,
                            "KakaoPay reconciliation identity or amount did not match.");
                    return;
                }
                applyConfirmedProviderResult(
                        candidate,
                        candidate.getProviderTransactionId(),
                        status,
                        objectText(response.get("payment_method_type")),
                        parseDateTime(objectText(response.get("approved_at")))
                );
            } else if ("READY".equals(status)) {
                applyTerminalProviderResult(candidate, status, "CANCELED");
            } else if (status.contains("CANCEL") || "QUIT_PAYMENT".equals(status)) {
                applyTerminalProviderResult(candidate, status, "CANCELED");
            } else if (status.contains("FAIL")) {
                applyTerminalProviderResult(candidate, status, "FAILED");
            } else {
                complete(candidate, "RETRY", status,
                        "KakaoPay payment remains pending.");
            }
        } catch (RuntimeException exception) {
            complete(candidate, "RETRY", "REQUEST_ERROR",
                    firstNonBlank(exception.getMessage(), "KakaoPay reconciliation request failed."));
        }
    }

    private void reconcileTossPayments(
            PaymentReconciliationCandidateDTO candidate
    ) {
        try {
            Map<String, Object> response = tossPaymentsService.findByOrderId(
                    candidate.getProviderTransactionId()
            );
            String status = objectText(response.get("status"));
            if ("DONE".equals(status)) {
                if (!candidate.getProviderTransactionId().equals(
                        objectText(response.get("orderId"))
                ) || !candidate.getAmount().equals(
                        objectInteger(response.get("totalAmount"))
                )) {
                    complete(candidate, "REVIEW_REQUIRED", status,
                            "Toss Payments reconciliation identity or amount did not match.");
                    return;
                }
                applyConfirmedProviderResult(
                        candidate,
                        objectText(response.get("paymentKey")),
                        status,
                        objectText(response.get("method")),
                        parseDateTime(objectText(response.get("approvedAt")))
                );
            } else if ("READY".equals(status)
                    || "CANCELED".equals(status)
                    || "PARTIAL_CANCELED".equals(status)
                    || "EXPIRED".equals(status)) {
                applyTerminalProviderResult(candidate, status, "CANCELED");
            } else if ("NOT_FOUND".equals(status)) {
                applyTerminalProviderResult(
                        candidate,
                        status,
                        "CANCELED"
                );
            } else if ("ABORTED".equals(status)) {
                applyTerminalProviderResult(candidate, status, "FAILED");
            } else {
                complete(candidate, "RETRY", status,
                        "Toss Payments payment remains pending.");
            }
        } catch (RuntimeException exception) {
            complete(candidate, "RETRY", "REQUEST_ERROR",
                    firstNonBlank(exception.getMessage(), "Toss Payments reconciliation request failed."));
        }
    }

    private void applyConfirmedProviderResult(
            PaymentReconciliationCandidateDTO candidate,
            String providerTransactionId,
            String providerStatus,
            String paymentMethod,
            LocalDateTime paidAt
    ) {
        PaymentProviderResultDTO result = paymentCommandMapper.applyProviderPaymentResult(
                candidate.getPaymentHistoryId(),
                candidate.getPaymentProvider(),
                providerTransactionId,
                providerStatus,
                paymentMethod,
                candidate.getAmount(),
                "SUCCESS",
                paidAt == null ? LocalDateTime.now() : paidAt,
                null
        );
        if (!isApplied(result)) {
            complete(candidate, "REVIEW_REQUIRED", providerStatus,
                    "Provider success could not be applied locally.");
        }
    }

    private void applyTerminalProviderResult(
            PaymentReconciliationCandidateDTO candidate,
            String providerStatus,
            String internalStatus
    ) {
        PaymentProviderResultDTO result = paymentCommandMapper.applyProviderPaymentResult(
                candidate.getPaymentHistoryId(),
                candidate.getPaymentProvider(),
                candidate.getProviderTransactionId(),
                providerStatus,
                null,
                candidate.getAmount(),
                internalStatus,
                null,
                "Provider reconciliation confirmed " + internalStatus.toLowerCase() + "."
        );
        if (!isApplied(result)) {
            complete(candidate, "REVIEW_REQUIRED", providerStatus,
                    "Provider terminal state could not be applied locally.");
        }
    }

    private boolean isApplied(PaymentProviderResultDTO result) {
        return result != null
                && ("APPLIED".equals(result.getResultCode())
                || "ALREADY_APPLIED".equals(result.getResultCode()));
    }

    private Map<String, String> createReadyCancelForm(String providerTransactionId) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("cmd", "paycancel");
        form.put("userid", payAppUserid);
        form.put("linkkey", payAppLinkKey);
        form.put("mul_no", providerTransactionId);
        form.put("cancelmemo", "Expired unpaid payment request reconciliation");
        form.put("cancelmode", "ready");
        form.put("partcancel", "0");
        return form;
    }

    private void complete(
            PaymentReconciliationCandidateDTO candidate,
            String outcome,
            String providerStatus,
            String message
    ) {
        try {
            String result = paymentCommandMapper.completePaymentReconciliation(
                    candidate.getPaymentHistoryId(),
                    outcome,
                    providerStatus,
                    message,
                    Math.max(retrySeconds, 60),
                    Math.max(maxAttempts, 1)
            );
            log.info(
                    "Payment reconciliation completed. paymentHistoryId={}, attempt={}, outcome={}, result={}",
                    candidate.getPaymentHistoryId(),
                    candidate.getReconciliationAttempts(),
                    outcome,
                    result
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Payment reconciliation result persistence failed. paymentHistoryId={}",
                    candidate.getPaymentHistoryId(),
                    exception
            );
        }
    }

    private boolean hasPayAppCredentials() {
        return payAppUserid != null
                && !payAppUserid.isBlank()
                && payAppLinkKey != null
                && !payAppLinkKey.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String objectText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer objectInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer nestedInteger(
            Map<String, Object> response,
            String objectKey,
            String valueKey
    ) {
        Object nested = response.get(objectKey);
        if (!(nested instanceof Map<?, ?> map)) {
            return null;
        }
        return objectInteger(map.get(valueKey));
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException exception) {
                return null;
            }
        }
    }
}
