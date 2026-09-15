package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.KakaoPayReadyDTO;
import kdt.washPrj.payment.dto.PaymentProviderContextDTO;
import kdt.washPrj.payment.dto.PaymentProviderResultDTO;
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
public class KakaoPayService {

    private static final String PROVIDER = PaymentProviderRegistry.KAKAOPAY;
    private static final String STATUS_WAITING = "WAITING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final String STATUS_FAILED = "FAILED";

    private final KakaoPayClient kakaoPayClient;
    private final PaymentQueryMapper paymentQueryMapper;
    private final PaymentCommandMapper paymentCommandMapper;
    private final String cid;
    private final String cidSecret;
    private final String callbackSecret;
    private final String backendBaseUrl;
    private final int callbackTokenSeconds;
    private final int reconciliationStaleSeconds;

    public KakaoPayService(
            KakaoPayClient kakaoPayClient,
            PaymentQueryMapper paymentQueryMapper,
            PaymentCommandMapper paymentCommandMapper,
            @Value("${kakaopay.cid:TC0ONETIME}") String cid,
            @Value("${kakaopay.cid-secret:}") String cidSecret,
            @Value("${kakaopay.callback-secret:}") String callbackSecret,
            @Value("${app.backend-base-url}") String backendBaseUrl,
            @Value("${kakaopay.callback-token-seconds:1200}") int callbackTokenSeconds,
            @Value("${payment.reconciliation.stale-seconds:${PAYMENT_RECONCILIATION_STALE_SECONDS:1800}}")
            int reconciliationStaleSeconds
    ) {
        this.kakaoPayClient = kakaoPayClient;
        this.paymentQueryMapper = paymentQueryMapper;
        this.paymentCommandMapper = paymentCommandMapper;
        this.cid = cid;
        this.cidSecret = cidSecret;
        this.callbackSecret = callbackSecret;
        this.backendBaseUrl = backendBaseUrl;
        this.callbackTokenSeconds = callbackTokenSeconds;
        this.reconciliationStaleSeconds = reconciliationStaleSeconds;
    }

    public KakaoPayReadyDTO readyCharge(
            Long paymentHistoryId,
            Long customerId,
            String orderName,
            Integer amount
    ) {
        return readyCharge(
                paymentHistoryId,
                customerId,
                orderName,
                amount,
                null
        );
    }

    public KakaoPayReadyDTO readyCharge(
            Long paymentHistoryId,
            Long customerId,
            String orderName,
            Integer amount,
            String returnClient
    ) {
        return ready(
                paymentHistoryId,
                "CUSTOMER-" + customerId,
                orderName,
                amount,
                returnClient
        );
    }

    public KakaoPayReadyDTO readyNonMember(
            Long paymentHistoryId,
            Long washHistoryId,
            String orderName,
            Integer amount
    ) {
        return ready(
                paymentHistoryId,
                "NON_MEMBER-" + washHistoryId,
                orderName,
                amount,
                null
        );
    }

    public PaymentProviderContextDTO approve(
            Long paymentHistoryId,
            String state,
            String pgToken
    ) {
        verifyState(paymentHistoryId, state);
        if (pgToken == null || pgToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "KakaoPay approval token is required."
            );
        }

        PaymentProviderContextDTO context = requireContext(paymentHistoryId);
        if (STATUS_SUCCESS.equals(context.getStatus())) {
            return context;
        }
        if (!STATUS_WAITING.equals(context.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment is not waiting for approval."
            );
        }

        String partnerOrderId = String.valueOf(paymentHistoryId);
        String partnerUserId = partnerUserId(context);
        Map<String, Object> request = baseRequest();
        request.put("tid", context.getProviderTransactionId());
        request.put("partner_order_id", partnerOrderId);
        request.put("partner_user_id", partnerUserId);
        request.put("pg_token", pgToken);

        Map<String, Object> response = kakaoPayClient.approve(request);
        validateApprovalResponse(response, context, partnerOrderId, partnerUserId);

        String providerTransactionId = text(response.get("tid"));
        String paymentMethod = text(response.get("payment_method_type"));
        LocalDateTime paidAt = parseDateTime(text(response.get("approved_at")));
        PaymentProviderResultDTO result = paymentCommandMapper.applyProviderPaymentResult(
                paymentHistoryId,
                PROVIDER,
                providerTransactionId,
                "SUCCESS_PAYMENT",
                paymentMethod,
                context.getAmount(),
                STATUS_SUCCESS,
                paidAt == null ? LocalDateTime.now() : paidAt,
                null
        );
        requireApplied(result);
        return requireContext(paymentHistoryId);
    }

    public PaymentProviderContextDTO cancelReadyPayment(
            Long paymentHistoryId,
            String state
    ) {
        verifyState(paymentHistoryId, state);
        PaymentProviderContextDTO context = requireContext(paymentHistoryId);
        applyTerminalState(context, "QUIT_PAYMENT", STATUS_CANCELED, "Customer canceled KakaoPay payment.");
        return requireContext(paymentHistoryId);
    }

    public PaymentProviderContextDTO failReadyPayment(
            Long paymentHistoryId,
            String state
    ) {
        verifyState(paymentHistoryId, state);
        PaymentProviderContextDTO context = requireContext(paymentHistoryId);
        applyTerminalState(context, "FAIL_PAYMENT", STATUS_FAILED, "KakaoPay payment failed.");
        return requireContext(paymentHistoryId);
    }

    public Map<String, Object> getOrder(String providerTransactionId) {
        Map<String, Object> request = baseRequest();
        request.put("tid", providerTransactionId);
        return kakaoPayClient.order(request);
    }

    public String cancelApprovedPayment(
            PaymentProviderContextDTO context,
            Integer refundAmount
    ) {
        if (context == null || context.getProviderTransactionId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "KakaoPay transaction id is missing."
            );
        }

        Map<String, Object> request = baseRequest();
        request.put("tid", context.getProviderTransactionId());
        request.put("cancel_amount", refundAmount);
        request.put("cancel_tax_free_amount", 0);
        Map<String, Object> response = kakaoPayClient.cancel(request);
        return "tid=" + text(response.get("tid"))
                + ";status=" + text(response.get("status"))
                + ";canceledAt=" + text(response.get("canceled_at"));
    }

    public PaymentProviderContextDTO requireContext(Long paymentHistoryId) {
        PaymentProviderContextDTO context = paymentQueryMapper
                .selectPaymentProviderContext(paymentHistoryId, PROVIDER);
        if (context == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "KakaoPay payment was not found."
            );
        }
        return context;
    }

    private KakaoPayReadyDTO ready(
            Long paymentHistoryId,
            String partnerUserId,
            String orderName,
            Integer amount,
            String returnClient
    ) {
        String state = createState(paymentHistoryId);
        Map<String, Object> request = baseRequest();
        request.put("partner_order_id", String.valueOf(paymentHistoryId));
        request.put("partner_user_id", partnerUserId);
        request.put("item_name", orderName);
        request.put("quantity", 1);
        request.put("total_amount", amount);
        request.put("tax_free_amount", 0);
        request.put("approval_url", callbackUrl("approve", paymentHistoryId, state, returnClient));
        request.put("cancel_url", callbackUrl("cancel", paymentHistoryId, state, returnClient));
        request.put("fail_url", callbackUrl("fail", paymentHistoryId, state, returnClient));

        Map<String, Object> response = kakaoPayClient.ready(request);
        String tid = requiredText(response, "tid");
        String pcUrl = requiredText(response, "next_redirect_pc_url");
        String mobileUrl = text(response.get("next_redirect_mobile_url"));
        String appUrl = text(response.get("next_redirect_app_url"));

        String recordResult = paymentCommandMapper.recordPaymentProviderRequest(
                paymentHistoryId,
                PROVIDER,
                tid,
                "READY",
                Math.max(reconciliationStaleSeconds, 60)
        );
        if (!"RECORDED".equals(recordResult)
                && !"ALREADY_FINAL".equals(recordResult)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "KakaoPay request could not be recorded."
            );
        }

        return new KakaoPayReadyDTO(
                tid,
                pcUrl,
                mobileUrl,
                appUrl,
                "READY"
        );
    }

    private void validateApprovalResponse(
            Map<String, Object> response,
            PaymentProviderContextDTO context,
            String partnerOrderId,
            String partnerUserId
    ) {
        if (!context.getProviderTransactionId().equals(text(response.get("tid")))
                || !partnerOrderId.equals(text(response.get("partner_order_id")))
                || !partnerUserId.equals(text(response.get("partner_user_id")))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "KakaoPay approval identity does not match the request."
            );
        }

        Integer approvedAmount = nestedInteger(response, "amount", "total");
        if (!context.getAmount().equals(approvedAmount)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "KakaoPay approval amount does not match."
            );
        }
    }

    private void applyTerminalState(
            PaymentProviderContextDTO context,
            String providerStatus,
            String internalStatus,
            String reason
    ) {
        if (!STATUS_WAITING.equals(context.getStatus())) {
            return;
        }
        PaymentProviderResultDTO result = paymentCommandMapper.applyProviderPaymentResult(
                context.getPaymentHistoryId(),
                PROVIDER,
                context.getProviderTransactionId(),
                providerStatus,
                null,
                context.getAmount(),
                internalStatus,
                null,
                reason
        );
        requireApplied(result);
    }

    private void requireApplied(PaymentProviderResultDTO result) {
        if (result == null
                || !("APPLIED".equals(result.getResultCode())
                || "ALREADY_APPLIED".equals(result.getResultCode()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "KakaoPay result could not be applied."
            );
        }
    }

    private Map<String, Object> baseRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("cid", cid);
        if (cidSecret != null && !cidSecret.isBlank()) {
            request.put("cid_secret", cidSecret);
        }
        return request;
    }

    private String callbackUrl(
            String action,
            Long paymentHistoryId,
            String state,
            String returnClient
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(backendBaseUrl)
                .path("/api/public/payments/kakaopay/")
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
        String payload = paymentHistoryId + ":" + expiresAt;
        return expiresAt + "." + sign(payload);
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
                    "KakaoPay callback state has expired."
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
                    "KakaoPay callback secret is not configured."
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
            throw new IllegalStateException("KakaoPay callback state could not be signed.", exception);
        }
    }

    private ResponseStatusException invalidState() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "KakaoPay callback state is invalid."
        );
    }

    private String partnerUserId(PaymentProviderContextDTO context) {
        if (context.getCustomerId() != null) {
            return "CUSTOMER-" + context.getCustomerId();
        }
        return "NON_MEMBER-" + context.getWashHistoryId();
    }

    private String requiredText(Map<String, Object> response, String key) {
        String value = text(response.get(key));
        if (value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "KakaoPay response is missing " + key + "."
            );
        }
        return value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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
        Object value = map.get(valueKey);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
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
