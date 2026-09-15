package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentProviderContextDTO;
import kdt.washPrj.payment.dto.PaymentProviderResultDTO;
import kdt.washPrj.payment.dto.TossPaymentReadyDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class TossPaymentsService {

    private static final String PROVIDER = PaymentProviderRegistry.TOSS_PAYMENTS;
    private static final String STATUS_WAITING = "WAITING";

    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentQueryMapper paymentQueryMapper;
    private final PaymentCommandMapper paymentCommandMapper;
    private final String clientKey;
    private final String callbackSecret;
    private final String backendBaseUrl;
    private final int callbackTokenSeconds;
    private final int reconciliationStaleSeconds;

    public TossPaymentsService(
            TossPaymentsClient tossPaymentsClient,
            PaymentQueryMapper paymentQueryMapper,
            PaymentCommandMapper paymentCommandMapper,
            @Value("${tosspayments.client-key:}") String clientKey,
            @Value("${tosspayments.callback-secret:}") String callbackSecret,
            @Value("${app.backend-base-url}") String backendBaseUrl,
            @Value("${tosspayments.callback-token-seconds:1200}") int callbackTokenSeconds,
            @Value("${payment.reconciliation.stale-seconds:${PAYMENT_RECONCILIATION_STALE_SECONDS:1800}}")
            int reconciliationStaleSeconds
    ) {
        this.tossPaymentsClient = tossPaymentsClient;
        this.paymentQueryMapper = paymentQueryMapper;
        this.paymentCommandMapper = paymentCommandMapper;
        this.clientKey = clientKey;
        this.callbackSecret = callbackSecret;
        this.backendBaseUrl = backendBaseUrl;
        this.callbackTokenSeconds = callbackTokenSeconds;
        this.reconciliationStaleSeconds = reconciliationStaleSeconds;
    }

    public TossPaymentReadyDTO ready(Long paymentHistoryId) {
        return ready(paymentHistoryId, null);
    }

    public TossPaymentReadyDTO ready(
            Long paymentHistoryId,
            String returnClient
    ) {
        String orderId = "ORDER-" + paymentHistoryId;
        String state = createState(paymentHistoryId);
        String recordResult = paymentCommandMapper.recordPaymentProviderRequest(
                paymentHistoryId,
                PROVIDER,
                orderId,
                "READY",
                Math.max(reconciliationStaleSeconds, 60)
        );
        if (!"RECORDED".equals(recordResult)
                && !"ALREADY_FINAL".equals(recordResult)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Toss Payments request could not be recorded."
            );
        }

        return new TossPaymentReadyDTO(
                clientKey,
                orderId,
                callbackUrl("success", paymentHistoryId, state, returnClient),
                callbackUrl("fail", paymentHistoryId, state, returnClient),
                "READY"
        );
    }

    public String checkoutUrl(
            TossPaymentReadyDTO ready,
            String orderName,
            Integer amount
    ) {
        return UriComponentsBuilder
                .fromUriString(backendBaseUrl)
                .path("/api/public/payments/tosspayments/checkout")
                .queryParam("clientKey", ready.getClientKey())
                .queryParam("orderId", ready.getOrderId())
                .queryParam("orderName", orderName)
                .queryParam("amount", amount)
                .queryParam("successUrl", ready.getSuccessUrl())
                .queryParam("failUrl", ready.getFailUrl())
                .build()
                .encode()
                .toUriString();
    }

    public PaymentProviderContextDTO confirm(
            Long paymentHistoryId,
            String state,
            String paymentKey,
            String orderId,
            Integer amount
    ) {
        verifyState(paymentHistoryId, state);
        PaymentProviderContextDTO context = requireContext(paymentHistoryId);
        if ("SUCCESS".equals(context.getStatus())) {
            return context;
        }
        if (!STATUS_WAITING.equals(context.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment is not waiting for confirmation."
            );
        }
        if (!context.getProviderTransactionId().equals(orderId)
                || !context.getAmount().equals(amount)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Toss Payments order information does not match."
            );
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("paymentKey", paymentKey);
        request.put("orderId", orderId);
        request.put("amount", amount);
        Map<String, Object> response = tossPaymentsClient.confirm(request);
        validateConfirmation(response, paymentKey, orderId, amount);

        PaymentProviderResultDTO result = paymentCommandMapper.applyProviderPaymentResult(
                paymentHistoryId,
                PROVIDER,
                paymentKey,
                text(response.get("status")),
                text(response.get("method")),
                amount,
                "SUCCESS",
                parseDateTime(text(response.get("approvedAt"))),
                null
        );
        requireApplied(result);
        return requireContext(paymentHistoryId);
    }

    public PaymentProviderContextDTO fail(
            Long paymentHistoryId,
            String state,
            String errorCode,
            String message
    ) {
        verifyState(paymentHistoryId, state);
        PaymentProviderContextDTO context = requireContext(paymentHistoryId);
        if (!STATUS_WAITING.equals(context.getStatus())) {
            return context;
        }

        String internalStatus = "PAY_PROCESS_CANCELED".equals(errorCode)
                ? "CANCELED"
                : "FAILED";
        PaymentProviderResultDTO result = paymentCommandMapper.applyProviderPaymentResult(
                paymentHistoryId,
                PROVIDER,
                context.getProviderTransactionId(),
                errorCode,
                null,
                context.getAmount(),
                internalStatus,
                null,
                normalizeMessage(message)
        );
        requireApplied(result);
        return requireContext(paymentHistoryId);
    }

    public Map<String, Object> findByOrderId(String orderId) {
        return tossPaymentsClient.findByOrderId(orderId);
    }

    public String cancelApprovedPayment(
            PaymentProviderContextDTO context,
            Integer refundAmount,
            String reason,
            String idempotencyKey
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("cancelReason", reason);
        request.put("cancelAmount", refundAmount);
        Map<String, Object> response = tossPaymentsClient.cancel(
                context.getProviderTransactionId(),
                request,
                idempotencyKey
        );
        return "paymentKey=" + text(response.get("paymentKey"))
                + ";status=" + text(response.get("status"));
    }

    public PaymentProviderContextDTO requireContext(Long paymentHistoryId) {
        PaymentProviderContextDTO context = paymentQueryMapper
                .selectPaymentProviderContext(paymentHistoryId, PROVIDER);
        if (context == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Toss Payments payment was not found."
            );
        }
        return context;
    }

    private void validateConfirmation(
            Map<String, Object> response,
            String paymentKey,
            String orderId,
            Integer amount
    ) {
        if (!paymentKey.equals(text(response.get("paymentKey")))
                || !orderId.equals(text(response.get("orderId")))
                || !amount.equals(integer(response.get("totalAmount")))
                || !"DONE".equals(text(response.get("status")))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss Payments confirmation does not match the request."
            );
        }
    }

    private void requireApplied(PaymentProviderResultDTO result) {
        if (result == null
                || !("APPLIED".equals(result.getResultCode())
                || "ALREADY_APPLIED".equals(result.getResultCode()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Toss Payments result could not be applied."
            );
        }
    }

    private String callbackUrl(
            String action,
            Long paymentHistoryId,
            String state,
            String returnClient
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(backendBaseUrl)
                .path("/api/public/payments/tosspayments/")
                .path(action)
                .queryParam("paymentId", paymentHistoryId)
                .queryParam("state", state);
        if ("FLUTTER_WEB".equalsIgnoreCase(returnClient)
                || "MOBILE".equalsIgnoreCase(returnClient)) {
            builder.queryParam("returnClient", returnClient.toUpperCase(Locale.ROOT));
        }
        return builder.build()
                .encode()
                .toUriString();
    }

    private String createState(Long paymentHistoryId) {
        long expiresAt = Instant.now().getEpochSecond()
                + Math.max(callbackTokenSeconds, 60);
        return expiresAt + "." + sign(paymentHistoryId + ":" + expiresAt);
    }

    private void verifyState(Long paymentHistoryId, String state) {
        if (state == null || !state.contains(".")) {
            throw invalidState();
        }
        String[] parts = state.split("\\.", 2);
        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[0]);
        } catch (NumberFormatException exception) {
            throw invalidState();
        }
        if (expiresAt < Instant.now().getEpochSecond()) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "Toss Payments callback state has expired."
            );
        }
        String expected = sign(paymentHistoryId + ":" + expiresAt);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                parts[1].getBytes(StandardCharsets.US_ASCII)
        )) {
            throw invalidState();
        }
    }

    private String sign(String payload) {
        if (callbackSecret == null || callbackSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Toss Payments callback secret is not configured."
            );
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    callbackSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Toss Payments callback state could not be signed.", exception);
        }
    }

    private ResponseStatusException invalidState() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Toss Payments callback state is invalid."
        );
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "Toss Payments authentication failed.";
        }
        return value.length() > 300 ? value.substring(0, 300) : value;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException exception) {
                return LocalDateTime.now();
            }
        }
    }
}
